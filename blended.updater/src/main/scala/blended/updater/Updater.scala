package blended.updater

import java.io.File
import java.util.concurrent.TimeUnit

import scala.collection.immutable._
import scala.concurrent.duration.Duration

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.{EventStream, LoggingReceive}
import blended.updater.config._
import blended.util.logging.Logger

class Updater(
  installBaseDir: File,
  config: UpdaterConfig,
  launchedProfileDir: Option[File],
  launchedProfileId: Option[ProfileRef]
) extends Actor
    with ActorLogging {

  import Updater._

  private val logger = Logger[Updater]

  /////////////////////
  // MUTABLE
  // requestId -> State
  private[this] var profiles: Map[ProfileRef, StatefulLocalProfile] = Map()

  private[this] var runtimeConfigs: Set[LocalProfile] = Set()

  private[this] var tickers: Seq[Cancellable] = Nil
  ////////////////////

  def findConfig(id: ProfileRef): Option[LocalProfile] = profiles.get(id).map(_.config)

  def findActiveConfig(): Option[LocalProfile] = findActiveProfile().map(_.config)

  def findActiveProfile(): Option[StatefulLocalProfile] = {
    launchedProfileId.flatMap(profileId => profiles.get(profileId))
  }

  /**
   * Signals to publish current service information into the Akka event stream.
   */
  case object PublishServiceInfo

  /**
   * Signals to publish current profile information into the Akka event stream.
   * Reply: none
   */
  case object PublishProfileInfo

  /**
   * Convenience accessor to event stream, also to better see where the event stream is used.
   * @return
   */
  private[this] def eventStream: EventStream = context.system.eventStream

  override def preStart(): Unit = {
    logger.info("Initiating initial scanning for profiles")
    self ! Scan

    if (config.serviceInfoIntervalMSec > 0) {
      logger.info(
        s"Enabling service info publishing [${config.serviceInfoIntervalMSec}]ms and lifetime [${config.serviceInfoLifetimeMSec}]ms"
      )
      implicit val eCtx = context.system.dispatcher
      tickers +:= context.system.scheduler.scheduleAtFixedRate(
        Duration(100, TimeUnit.MILLISECONDS),
        Duration(config.serviceInfoIntervalMSec, TimeUnit.MILLISECONDS)
      ) { () =>
        self ! PublishServiceInfo
        self ! PublishProfileInfo
      }
    } else {
      logger.info("Publishing of service infos and profile infos is disabled")
    }

    super.preStart()
  }

  override def postStop(): Unit = {

    tickers.foreach { t =>
      logger.info(s"Disabling ticker: ${t}")
      t.cancel()
    }
    tickers = Nil
    super.postStop()
  }

  def handleProtocol(msg: Protocol): Unit =
    msg match {

      case GetRuntimeConfigs(reqId) =>
        sender() ! Result(reqId, runtimeConfigs)

      case GetProfiles(reqId) =>
        sender() ! Result(reqId, profiles.values.toSet)

      case GetProfileIds(reqId) =>
        sender() ! Result(reqId, profiles.keySet)
    }

  def scanForRuntimeConfigs(): List[LocalProfile] = {
    ProfileFsHelper.scanForRuntimeConfigs(installBaseDir)
  }

  def scanForProfiles(runtimeConfigs: Option[List[LocalProfile]] = None): List[StatefulLocalProfile] = {
    ProfileFsHelper.scanForProfiles(installBaseDir, runtimeConfigs)
  }

  override def receive: Actor.Receive =
    LoggingReceive {

      // direct protocol
      case p: Protocol =>
        logger.debug(s"Handling Protocol message: ${p}")
        handleProtocol(p)

      case Scan =>
        logger.debug("Handling Scan mesage")
        val rcs = scanForRuntimeConfigs()
        runtimeConfigs = rcs.toSet

        val fullProfiles = scanForProfiles(Option(rcs))
        profiles = fullProfiles.map { profile =>
          profile.profileId -> profile
        }.toMap
        logger.debug(s"Profiles (after scan): ${profiles}")

      case PublishProfileInfo =>
        logger.debug("Handling PublishProfileInfo message")
        val activeProfile = findActiveProfile().map(_.toSingleProfile)
        val singleProfiles = profiles.values.toList.map(_.toSingleProfile).map { p =>
          activeProfile match {
            case Some(a) if p.name == a.name && p.version == a.version => p
            case _                                                     => p
          }

        }
        val toSend = singleProfiles
        logger.debug(s"Publishing profile info to event stream: ${toSend}")
        eventStream.publish(ProfileInfo(System.currentTimeMillis(), toSend))

      case PublishServiceInfo =>
        logger.debug("Handling PublishServiceInfo message")

        val serviceInfo = ServiceInfo(
          name = context.self.path.toString,
          serviceType = "Updater",
          timestampMsec = System.currentTimeMillis(),
          lifetimeMsec = config.serviceInfoLifetimeMSec,
          props = Map(
            "installBaseDir" -> installBaseDir.getAbsolutePath(),
            "launchedProfileDir" -> launchedProfileDir.map(_.getAbsolutePath()).getOrElse(""),
            "launchedProfileId" -> launchedProfileId.map(_.toString()).getOrElse("")
          )
        )
        logger.debug(s"About to publish service info: ${serviceInfo}")
        eventStream.publish(serviceInfo)

    }

}

object Updater {

  /**
   * Supported Messages by the [[Updater]] actor.
   */
  sealed trait Protocol {
    def requestId: String
  }

  //  /**
  //   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
  //   * FIXME: rename to GetProfiles
  //   */
  //  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol

  final case class GetRuntimeConfigs(override val requestId: String) extends Protocol

  /**
   * Get all known profiles.
   * Reply: [[Result[Set[LocalProfile]]]]
   */
  final case class GetProfiles(override val requestId: String) extends Protocol

  /**
   * Get all known profile ids.
   * Reply: Result[Set[ProfileId]]
   */
  final case class GetProfileIds(override val requestId: String) extends Protocol

  /**
   * Internal message: Scans the profile directory for existing runtime configurations
   * and replaces the internal state of this actor with the result.
   * Reply: none
   */
  private final case object Scan

  /**buid
   * Supported Replies by the [[Updater]] actor.
   */
  sealed trait Reply

  final case class Result[T](requestId: String, result: T) extends Reply

  final case class OperationSucceeded(requestId: String) extends Reply

  final case class OperationFailed(requestId: String, reason: String) extends Reply

  /**
   * Create the actor properties.
   */
  def props(
    baseDir: File,
    config: UpdaterConfig,
    launchedProfileDir: File = null,
    launchedProfileRef: ProfileRef = null
  ): Props = {

    Props(
      new Updater(
        installBaseDir = baseDir,
        config,
        Option(launchedProfileDir),
        Option(launchedProfileRef)
      )
    )
  }

  /**
   * A bundle in progress, e.g. downloading or verifying.
   */
  private case class ArtifactInProgress(reqId: String, artifact: Artifact, file: File)

  /**
   * Internal working state of in-progress stagings.
   */
  private case class State(
    requestId: String,
    requestActor: ActorRef,
    config: LocalProfile,
    artifactsToDownload: List[ArtifactInProgress],
    pendingArtifactsToUnpack: List[ArtifactInProgress],
    artifactsToUnpack: List[ArtifactInProgress],
    issues: List[String]
  ) {

    val profileRef = ProfileRef(config.runtimeConfig.name, config.runtimeConfig.version)

    /**
     * The download/unpack progress in percent.
     */
    def progress(): Int = {
      val all = config.runtimeConfig.bundles.size
      val todos = artifactsToDownload.size
      if (all > 0)
        (100 / all) * (all - todos)
      else 100
    }

  }

}
