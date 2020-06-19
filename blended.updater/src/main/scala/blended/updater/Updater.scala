package blended.updater

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.collection.immutable._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.{EventStream, LoggingReceive}
import akka.pattern.ask
import akka.routing.BalancingPool
import akka.util.Timeout
import blended.updater.config.{
  UpdateAction,
  ActivateProfile => UAActivateProfile,
  AddRuntimeConfig => UAAddRuntimeConfig,
  StageProfile => UAStageProfile,
  _
}
import blended.util.logging.Logger

class Updater(
    installBaseDir: File,
    profileActivator: ProfileActivator,
    restartFramework: () => Unit,
    config: UpdaterConfig,
    launchedProfileDir: Option[File],
    launchedProfileId: Option[ProfileId]
) extends Actor
    with ActorLogging {

  import Updater._

  private[this] val log = Logger[Updater]

  val artifactDownloader = context.actorOf(
    BalancingPool(config.artifactDownloaderPoolSize).props(ArtifactDownloader.props(config.mvnRepositories)),
    "artifactDownloader"
  )
  val unpacker = context.actorOf(
    BalancingPool(config.unpackerPoolSize).props(Unpacker.props()),
    "unpacker"
  )

  /////////////////////
  // MUTABLE
  // requestId -> State
  private[this] var stagingInProgress: Map[String, State] = Map()

  private[this] var profiles: Map[ProfileId, LocalProfile] = Map()

  private[this] var runtimeConfigs: Set[LocalRuntimeConfig] = Set()

  private[this] var tickers: Seq[Cancellable] = Nil
  ////////////////////

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug(s"Progress: ${progress} for reqestId: ${id}")

    // TODO: generate config files (overlay)

    if (state.artifactsToDownload.isEmpty && state.issues.isEmpty && !state.pendingArtifactsToUnpack.isEmpty) {
      // start unpacking
      state.pendingArtifactsToUnpack.foreach { a =>
        unpacker ! Unpacker.Unpack(a.reqId, self, a.file, config.baseDir)
      }
      stageInProgress(
        state.copy(
          artifactsToUnpack = state.pendingArtifactsToUnpack,
          pendingArtifactsToUnpack = Nil
        ))
    } else if (state.artifactsToDownload.isEmpty && state.artifactsToUnpack.isEmpty) {

      stagingInProgress = stagingInProgress.view.filterKeys(id != _).toMap
      val (profileState, msg) = state.issues match {
        case Seq()  => LocalProfile.Staged -> OperationSucceeded(id)
        case issues => LocalProfile.Invalid(issues) -> OperationFailed(id, issues.mkString("; "))
      }

      profiles += state.profileId -> LocalProfile(state.config, profileState)
      state.requestActor ! msg

    } else {
      stagingInProgress += id -> state
    }
  }

  def findConfig(id: ProfileId): Option[LocalRuntimeConfig] = profiles.get(id).map(_.config)

  def findActiveConfig(): Option[LocalRuntimeConfig] = findActiveProfile().map(_.config)

  def findActiveProfile(): Option[LocalProfile] = {
    launchedProfileId.flatMap(profileId => profiles.get(profileId))
  }

  def findLocalResource(name: String, sha1Sum: String): Option[File] =
    profiles.values
      .filter { profile =>
        // only valid profiles
        profile.state == LocalProfile.Staged
      }
      .flatMap { profile =>
        profile.bundles
          .filter { b =>
            // same sha1sum
            b.sha1Sum == Some(sha1Sum)
          }
          .filter { b =>
            val location = profile.config.bundleLocation(b.artifact)
            // same file name and file exists
            location.getName() == name && location.exists()
          }
          .map { b =>
            // extract location
            profile.config.bundleLocation(b.artifact)
          }
          .headOption
      }
      .headOption

  private[this] def nextId(): String = UUID.randomUUID().toString()

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
    log.info("Initiating initial scanning for profiles")
    self ! Scan

    if (config.autoStagingIntervalMSec > 0) {
      log.info(
        s"Enabling auto-staging with interval [${config.autoStagingIntervalMSec}] and initial delay [${config.autoStagingDelayMSec}]")
      implicit val eCtx = context.system.dispatcher
      tickers +:= context.system.scheduler.scheduleAtFixedRate(
        Duration(config.autoStagingDelayMSec, TimeUnit.MILLISECONDS),
        Duration(config.autoStagingIntervalMSec, TimeUnit.MILLISECONDS)
      )(() => self ! StageNextRuntimeConfig(nextId()))
    } else {
      log.info(s"Auto-staging is disabled")
    }

    if (config.serviceInfoIntervalMSec > 0) {
      log.info(
        s"Enabling service info publishing [${config.serviceInfoIntervalMSec}] and lifetime [${config.serviceInfoLifetimeMSec}]")
      implicit val eCtx = context.system.dispatcher
      tickers +:= context.system.scheduler.scheduleAtFixedRate(
        Duration(100, TimeUnit.MILLISECONDS),
        Duration(config.serviceInfoIntervalMSec, TimeUnit.MILLISECONDS)
      ) { () =>
        self ! PublishServiceInfo
        self ! PublishProfileInfo
      }
    } else {
      log.info("Publishing of service infos and profile infos is disabled")
    }

    eventStream.subscribe(context.self, classOf[UpdateAction])

    super.preStart()
  }

  override def postStop(): Unit = {

    eventStream.unsubscribe(context.self)

    tickers.foreach { t =>
      log.info(s"Disabling ticker: ${t}")
      t.cancel()
    }
    tickers = Nil
    super.postStop()
  }

  private def publishResultEvent[T](id: String, reply: Try[Any]) = {
    reply match {
      case Success(OperationSucceeded(_))      => eventStream.publish(UpdateActionApplied(id))
      case Success(OperationFailed(_, reason)) => eventStream.publish(UpdateActionApplied(id, Option(reason)))
      case Failure(e)                          => eventStream.publish(UpdateActionApplied(id, Option(e.getMessage())))
      case x                                   => log.warn(s"Skip publish event of unsupported reply: [${x}]")
    }
  }

  def handleUpdateAction(event: UpdateAction): Unit = event match {
    case UAAddRuntimeConfig(id, runtimeConfig) =>
      log.debug(
        s"Received add runtime config request (via event stream) for ${runtimeConfig.name}-${runtimeConfig.version} with ID [${id}]")

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(AddRuntimeConfig(nextId(), runtimeConfig))(timeout).onComplete { x =>
        log.debug(
          s"Finished add runtime config request (via event stream) for ${runtimeConfig.name}-${runtimeConfig.version} with ID [${id}] with result: ${x}")
        publishResultEvent(id, x)
      }

    case UAStageProfile(id, name, version) =>
      log.debug(s"Received stage profile request (via event stream) for ${name}-${version} with ID [${id}]")

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      val request = StageProfile(nextId(), name, version)
      self.ask(request)(timeout).onComplete {
        case x @ Success(OperationFailed(_, reason)) =>
          log.error(s"Could not stage profile: ${reason}")
          publishResultEvent(id, x)
        case x @ Failure(e) =>
          log.error(e)(s"Could not complete stage profile [${request}]")
          publishResultEvent(id, x)
        case x =>
          log.debug(
            s"Finished stage profile request (via event stream) for ${name}-${version} with ID [${id}] with result: ${x}")
          publishResultEvent(id, x)
      }

    case UAActivateProfile(id, name, version) =>
      log.debug(s"Received activate profile request (via event stream) for ${name}-${version} with ID [${id}]")

      implicit val ec = context.system.dispatcher
      val timeout = new Timeout(10, TimeUnit.MINUTES)

      self.ask(ActivateProfile(nextId(), name, version))(timeout).onComplete { x =>
        log.debug(
          s"Finished activation profile request (via event stream) for ${name}-${version} with ID [${id}] with result: ${x}")
        publishResultEvent(id, x)
      }
  }

  protected def activateProfile(reqActor: ActorRef, msg: ActivateProfile): Unit = {
    val ActivateProfile(reqId, name, version) = msg
    val profileId = ProfileId(name, version)
    log.debug(s"Requested activate profile with id: ${profileId}")

    profiles.get(profileId) match {
      case Some(LocalProfile(_, LocalProfile.Staged)) =>
        // write config
        log.debug(s"About to activate new profile for next startup: ${profileId}")
        val success = profileActivator(name, version)
        if (success) {
          reqActor ! OperationSucceeded(reqId)
          restartFramework()
        } else {
          reqActor ! OperationFailed(reqId, "Could not update next startup profile")
        }
      case r =>
        log.debug(s"Could not find staged profile with id ${profileId} but: ${r}")
        log.trace(s"All known profiles: ${profiles.keySet}")
        reqActor ! OperationFailed(reqId, "No such staged runtime configuration found")
    }

  }

  protected def stageProfile(reqActor: ActorRef, msg: StageProfile): Unit = {
    val StageProfile(reqId, name, version) = msg

    val profileId = ProfileId(name, version)

    // try to find the profile, if not found, we report a failure to the reqActor
    val foundProfile: Option[LocalProfile] = profiles.get(profileId).orElse {
      Try {
        val localRuntimeConfig =
          runtimeConfigs.find(r => r.runtimeConfig.name == name && r.runtimeConfig.version == version) match {
            case None     => sys.error(s"No such runtime config found: ${name}-${version}")
            case Some(rc) => rc
          }

        LocalProfile(localRuntimeConfig, LocalProfile.Pending(List("Newly staged")))

      } match {
        case Failure(e) =>
          reqActor ! OperationFailed(reqId, e.getMessage())
          None
        case Success(p) => Some(p)
      }
    }

    // we found a profile and inspect it
    foundProfile.foreach {
      case profile @ LocalProfile(_, LocalProfile.Staged) =>
        // already staged
        log.debug(s"Profile [${profile.profileId}] already staged")
        reqActor ! OperationSucceeded(reqId)

      case profile @ LocalProfile(config, state) =>
        log.debug(s"About to stage profile [${profile.profileId}] with current state [$state]")

        if (stagingInProgress.contains(reqId)) {
          log.error(s"Duplicate id detected. Dropping request [${msg}]")
        } else {
          val artifacts = config.resolvedProfile.allBundles.map { b =>
            ArtifactInProgress(nextId(), b.artifact, config.bundleLocation(b))
          } ++
            config.runtimeConfig.resources.map { r =>
              ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r))
            }

          val pendingUnpacks = config.runtimeConfig.resources.map { r =>
            ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r))
          }

          artifacts.foreach { a =>
            artifactDownloader ! ArtifactDownloader.Download(a.reqId, a.artifact, a.file)
          }

          stageInProgress(
            State(
              requestId = reqId,
              requestActor = reqActor,
              config = config,
              artifactsToDownload = artifacts,
              pendingArtifactsToUnpack = pendingUnpacks,
              artifactsToUnpack = List.empty,
              issues = List.empty
            ))

        }
    }

  }

  def handleProtocol(msg: Protocol): Unit = msg match {

    case GetRuntimeConfigs(reqId) =>
      sender() ! Result(reqId, runtimeConfigs)

    case GetProfiles(reqId) =>
      sender() ! Result(reqId, profiles.values.toSet)

    case GetProfileIds(reqId) =>
      sender() ! Result(reqId, profiles.keySet)

    case AddRuntimeConfig(reqId, config) =>
      runtimeConfigs.find(r => r.runtimeConfig.name == config.name && r.runtimeConfig.version == config.version) match {
        case Some(existing) if existing.runtimeConfig != config =>
          sender() ! OperationFailed(
            reqId,
            s"A different overlay with same name and version (${config.name}-${config.version}) is already registered")
        case _ =>
          val confFile = new File(installBaseDir, s"${config.name}/${config.version}/profile.conf")
          config.resolve() match {
            case Success(resolved) =>
              confFile.getParentFile().mkdirs()
              ConfigWriter.write(ProfileCompanion.toConfig(config), confFile, None)
              runtimeConfigs += LocalRuntimeConfig(baseDir = confFile.getParentFile(), resolvedProfile = resolved)
              sender() ! OperationSucceeded(reqId)
            case Failure(e) =>
              sender() ! OperationFailed(reqId, s"Given runtime config can't be resolved: ${e.getMessage}")
          }
      }

    case StageNextRuntimeConfig(reqId @ _) =>
      if (stagingInProgress.isEmpty) {
        profiles.iterator
          .collect {
            case (id @ ProfileId(name, version), LocalProfile(_, LocalProfile.Pending(_))) =>
              log.info(s"About to auto-stage profile ${id}")
              self ! StageProfile(nextId(), name, version)
          }
          .take(1)
      }

    case msg: StageProfile => stageProfile(sender(), msg)

    case msg: ActivateProfile => activateProfile(sender(), msg)

    case GetProgress(reqId) =>
      val reqActor = sender()
      stagingInProgress.get(reqId) match {
        case Some(state) => reqActor ! Progress(reqId, state.progress())
        case None        => reqActor ! OperationFailed(reqId, "Unknown request ID")
      }

  }

  def scanForRuntimeConfigs(): List[LocalRuntimeConfig] = {
    ProfileFsHelper.scanForRuntimeConfigs(installBaseDir)
  }

  def scanForProfiles(runtimeConfigs: Option[List[LocalRuntimeConfig]] = None): List[LocalProfile] = {
    ProfileFsHelper.scanForProfiles(installBaseDir, runtimeConfigs)
  }

  override def receive: Actor.Receive = LoggingReceive {

    // from event stream
    case e: UpdateAction =>
      log.debug(s"Current profiles [${profiles}]")
      log.debug(s"Handling UpdateAction message: ${e}")
      handleUpdateAction(e)

    // direct protocol
    case p: Protocol =>
      log.debug(s"Handling Protocol message: ${p}")
      handleProtocol(p)

    case Scan =>
      log.debug("Handling Scan mesage")
      val rcs = scanForRuntimeConfigs()
      runtimeConfigs = rcs.toSet

      val fullProfiles = scanForProfiles(Option(rcs))
      profiles = fullProfiles.map { profile =>
        profile.profileId -> profile
      }.toMap
      log.debug(s"Profiles (after scan): ${profiles}")

    case PublishProfileInfo =>
      log.debug("Handling PublishProfileInfo message")
      val activeProfile = findActiveProfile().map(_.toSingleProfile)
      val singleProfiles = profiles.values.toList.map(_.toSingleProfile).map { p =>
        activeProfile match {
          case Some(a) if p.name == a.name && p.version == a.version => p
          case _                                                     => p
        }

      }
      val toSend = singleProfiles
      log.debug(s"Publishing profile info to event stream: ${toSend}")
      eventStream.publish(ProfileInfo(System.currentTimeMillis(), toSend))

    case PublishServiceInfo =>
      log.debug("Handling PublishServiceInfo message")

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
      log.debug(s"About to publish service info: ${serviceInfo}")
      eventStream.publish(serviceInfo)

    case msg: ArtifactDownloader.Reply => handleArtifactDownloaderReply(msg)

    case msg: Unpacker.UnpackReply => handleUnpackerUnpackReply(msg)
  }

  protected def handleArtifactDownloaderReply(msg: ArtifactDownloader.Reply): Unit = {
    val foundProgress = stagingInProgress.values.flatMap { state =>
      state.artifactsToDownload
        .find { bip =>
          bip.reqId == msg.requestId
        }
        .map(state -> _)
        .toList
    }.toList
    foundProgress match {
      case Nil =>
        log.error(s"Unkown download id ${msg.requestId}")
      case (state, bundleInProgress) :: _ =>
        val newArtifactsToDownload = state.artifactsToDownload.filter(bundleInProgress != _)
        msg match {
          case ArtifactDownloader.DownloadFinished(_) =>
            stageInProgress(
              state.copy(
                artifactsToDownload = newArtifactsToDownload
              ))
          case ArtifactDownloader.DownloadFailed(_, error) =>
            stageInProgress(
              state.copy(
                artifactsToDownload = newArtifactsToDownload,
                issues = s"Download failed for ${bundleInProgress.file} (${error.getClass().getSimpleName()}: ${error})" +: state.issues
              ))
        }
    }
  }

  protected def handleUnpackerUnpackReply(msg: Unpacker.UnpackReply): Unit = {
    val foundProgress = stagingInProgress.values.flatMap { state =>
      state.artifactsToUnpack
        .find { a =>
          a.reqId == msg.reqId
        }
        .map(state -> _)
        .toList
    }.toList
    foundProgress match {
      case Nil =>
        log.error(s"Unkown unpack process ${msg.reqId}")
      case (state, artifact) :: _ =>
        val artifactsToUnpack = state.artifactsToUnpack.filter(artifact != _)
        msg match {
          case Unpacker.UnpackingFinished(_) =>
            state.config.createResourceArchiveTouchFile(artifact.artifact, artifact.artifact.sha1Sum) match {
              case Success(file) =>
                stageInProgress(
                  state.copy(
                    artifactsToUnpack = artifactsToUnpack
                  ))
              case Failure(e) =>
                stageInProgress(
                  state.copy(
                    artifactsToUnpack = artifactsToUnpack,
                    issues = s"Could not create unpacked-marker file for resource file ${artifact.file} (${e
                      .getClass()
                      .getSimpleName()}: ${e.getMessage()})" +: state.issues
                  ))
            }
          case Unpacker.UnpackingFailed(_, e) =>
            stageInProgress(
              state.copy(
                artifactsToUnpack = artifactsToUnpack,
                issues = s"Could not unpack file ${artifact.file} (${e.getClass().getSimpleName()}: ${e.getMessage()})" +: state.issues
              ))
        }
    }
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
   * Get a progress for the given `requestId`.
   * Reply: `Result[Progress]`
   */
  final case class GetProgress(override val requestId: String) extends Protocol

  /**
   * Register a (new) runtime config.
   * Reply: [[OperationSucceeded]], [[OperationFailed]]
   */
  final case class AddRuntimeConfig(override val requestId: String, runtimeConfig: Profile) extends Protocol

  /**
   * Stage a profile, which is a runtime config with an overlay set.
   * Reply: [[OperationSucceeded]], [[OperationSucceeded]]
   */
  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  final case class StageProfile(override val requestId: String, name: String, version: String) extends Protocol

  final case class ActivateProfile(override val requestId: String, name: String, version: String) extends Protocol

  /**
   * Internal message: Scans the profile directory for existing runtime configurations
   * and replaces the internal state of this actor with the result.
   * Reply: none
   */
  private final case object Scan

  /**
   * Stage the next runtime config, or do nothing if there is no next runtime config.
   */
  final case class StageNextRuntimeConfig(override val requestId: String) extends Protocol

  /**
   * Supported Replies by the [[Updater]] actor.
   */
  sealed trait Reply

  final case class Progress(requestId: String, progress: Int) extends Reply

  final case class Result[T](requestId: String, result: T) extends Reply

  final case class OperationSucceeded(requestId: String) extends Reply

  final case class OperationFailed(requestId: String, reason: String) extends Reply

  /**
   * Create the actor properties.
   */
  def props(
      baseDir: File,
      profileActivator: ProfileActivator,
      restartFramework: () => Unit,
      config: UpdaterConfig,
      launchedProfileDir: File = null,
      launchedProfileId: ProfileId = null
  ): Props = {

    Props(
      new Updater(
        installBaseDir = baseDir,
        profileActivator = profileActivator,
        restartFramework = restartFramework,
        config,
        Option(launchedProfileDir),
        Option(launchedProfileId)
      ))
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
      config: LocalRuntimeConfig,
      artifactsToDownload: List[ArtifactInProgress],
      pendingArtifactsToUnpack: List[ArtifactInProgress],
      artifactsToUnpack: List[ArtifactInProgress],
      issues: List[String]
  ) {

    val profileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version)

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
