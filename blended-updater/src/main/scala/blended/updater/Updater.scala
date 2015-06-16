package blended.updater

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import akka.routing.BalancingPool
import blended.updater.BlockingDownloader.Download
import blended.updater.BlockingDownloader.DownloadFailed
import blended.updater.BlockingDownloader.DownloadFinished
import blended.updater.Sha1SumChecker.CheckFile
import blended.updater.Sha1SumChecker.InvalidChecksum
import blended.updater.Sha1SumChecker.ValidChecksum
import blended.updater.config.ConfigConverter
import blended.updater.config.ConfigWriter
import blended.updater.config.LauncherConfig
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import org.osgi.framework.BundleContext
import scala.collection.immutable._
import scala.util.control.NonFatal
import scala.concurrent.duration.Duration

object Updater {

  /**
   * Supported Messages by the [[Updater]] actor.
   */
  sealed trait Protocol {
    def requestId: String
  }
  /**
   * Supported Replies by the [[Updater]] actor.
   */
  trait Reply

  /**
   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
   */
  case class GetRuntimeConfigs(requestId: String) extends Protocol
  case class RuntimeConfigs(requestId: String, unstaged: Seq[RuntimeConfig], staged: Seq[RuntimeConfig]) extends Reply

  case class AddRuntimeConfig(requestId: String, runtimeConfig: RuntimeConfig) extends Protocol
  case class RuntimeConfigAdded(requestId: String) extends Reply
  case class RuntimeConfigAdditionFailed(requestId: String, reason: String) extends Reply

  case class ScanForRuntimeConfigs(requestId: String) extends Protocol

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  case class StageRuntimeConfig(requestId: String, name: String, version: String) extends Protocol
  case class StageNextRuntimeConfig(requestId: String) extends Protocol
  case class RuntimeConfigStaged(requestId: String) extends Reply
  case class RuntimeConfigStagingFailed(requestId: String, reason: String) extends Reply

  case class ActivateRuntimeConfig(requestId: String, name: String, version: String) extends Protocol
  case class RuntimeConfigActivated(requestId: String) extends Reply
  case class RuntimeConfigActivationFailed(requestId: String, reason: String) extends Reply

  case class GetProgress(requestId: String) extends Protocol
  case class Progress(requestId: String, progress: Int) extends Reply

  case class UnknownRuntimeConfig(requestId: String) extends Reply
  case class UnknownRequestId(requestId: String) extends Reply

  def props(
    baseDir: File,
    launcherConfigSetter: LauncherConfig => Unit,
    restartFramework: () => Unit,
    artifactDownloaderProps: Props = null,
    artifactCheckerProps: Props = null): Props =
    Props(new Updater(
      baseDir,
      launcherConfigSetter,
      restartFramework,
      Option(artifactDownloaderProps),
      Option(artifactCheckerProps)
    ))

  /**
   * A bundle in progress, e.g. downloading or verifying.
   */
  private case class BundleInProgress(reqId: String, bundle: RuntimeConfig.BundleConfig, file: File)

  /**
   * Internal working state of in-progress stagings.
   */
  private case class State(
      requestId: String,
      requestActor: ActorRef,
      config: RuntimeConfig,
      installDir: File,
      bundlesToDownload: Seq[BundleInProgress],
      bundlesToCheck: Seq[BundleInProgress],
      issues: Seq[String]) {

    val profileId = ProfileId(config.name, config.version)

    def progress(): Int = {
      val allBundlesSize = config.bundles.size
      if (allBundlesSize > 0)
        (100 / allBundlesSize) * (allBundlesSize - bundlesToDownload.size - bundlesToCheck.size)
      else 100
    }

  }

  case class ProfileId(name: String, version: String)

  object Profile {
    sealed trait ProfileState
    case class Pending(issues: Seq[String]) extends ProfileState
    case class Invalid(issues: Seq[String]) extends ProfileState
    case object Valid extends ProfileState
  }

  case class Profile(dir: File, config: RuntimeConfig, state: Profile.ProfileState) {
    def profile: ProfileId = ProfileId(config.name, config.version)
  }

}

// TODO: Move profiles with persisting issues into invalid state
// TODO: Move auto-staging enablement and interval into config
class Updater(
  installBaseDir: File,
  launchConfigSetter: LauncherConfig => Unit,
  restartFramework: () => Unit,
  artifactDownloaderProps: Option[Props],
  artifactCheckerProps: Option[Props])
    extends Actor
    with ActorLogging {
  import Updater._

  val artifactDownloader = context.actorOf(
    artifactDownloaderProps.getOrElse(BalancingPool(4).props(BlockingDownloader.props())),
    "artifactDownloader")
  val artifactChecker = context.actorOf(
    artifactCheckerProps.getOrElse(BalancingPool(4).props(Sha1SumChecker.props())),
    "artifactChecker")

  private[this] var stagingInProgress: Map[String, State] = Map()

  private[this] var profiles: Map[ProfileId, Profile] = Map()

  private[this] var stageProfilesTicker: Option[Cancellable] = None

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

    if (state.bundlesToCheck.isEmpty && state.bundlesToDownload.isEmpty) {
      stagingInProgress = stagingInProgress.filterKeys(id != _)
      val (profileState, msg) = state.issues match {
        case Seq() => Profile.Valid -> RuntimeConfigStaged(id)
        case issues => Profile.Invalid(issues) -> RuntimeConfigStagingFailed(id, issues.mkString("; "))
      }
      profiles += state.profileId -> Profile(state.installDir, state.config, profileState)
      state.requestActor ! msg
    } else {
      stagingInProgress += id -> state
    }
  }

  def findConfig(id: ProfileId): Option[RuntimeConfig] = profiles.get(id).map(_.config)

  private[this] def nextId(): String = UUID.randomUUID().toString()

  override def preStart(): Unit = {
    log.info("Initial scanning for profiles")
    self ! ScanForRuntimeConfigs(UUID.randomUUID().toString())

    log.info("Enabling auto-staging")
    implicit val eCtx = context.system.dispatcher
    stageProfilesTicker = Some(context.system.scheduler.schedule(Duration(1, TimeUnit.MINUTES), Duration(5, TimeUnit.MINUTES)) {
      self ! StageNextRuntimeConfig(nextId())
    })

    super.preStart()

  }

  override def postStop(): Unit = {
    stageProfilesTicker.foreach { t =>
      log.info("Disabling auto-staging")
      t.cancel()
    }
    stageProfilesTicker = None
    super.postStop()
  }

  def protocol(msg: Protocol): Unit = msg match {

    case ScanForRuntimeConfigs(reqId) =>
      // profileFiles
      val confs = Option(installBaseDir.listFiles).getOrElse(Array()).toList.
        flatMap { nameDir =>
          Option(nameDir.listFiles).getOrElse(Array()).toList.
            flatMap { versionDir =>
              val profileFile = new File(versionDir, "profile.conf")
              if (profileFile.exists()) Some(profileFile)
              else None
            }
        }

      log.info("Detected profile configs : {}", confs)

      // read configs
      val foundProfiles = confs.flatMap { profileFile =>
        val versionDir = profileFile.getParentFile()
        val version = versionDir.getName()
        val name = versionDir.getParentFile.getName()
        try {
          val config = ConfigFactory.parseFile(profileFile).resolve()
          val runtimeConfig = RuntimeConfig.read(config)
          if (runtimeConfig.name == name && runtimeConfig.version == version) {
            val issues = RuntimeConfig.validate(versionDir, runtimeConfig)
            val profileState = issues match {
              case Seq() => Profile.Valid
              case issues => Profile.Pending(issues)
            }
            List(Profile(versionDir, runtimeConfig, profileState))
          } else List()
        } catch {
          case NonFatal(e) => List()
        }
      }

      profiles = foundProfiles.map { profile => profile.profile -> profile }.toMap

    case GetRuntimeConfigs(reqId) =>
      val (staged, unstaged) = profiles.values.toList.partition { p =>
        p.state match {
          case Profile.Valid => true
          case _ => false
        }
      }
      sender() ! RuntimeConfigs(reqId,
        unstaged = unstaged.map(_.config),
        staged = staged.map(_.config)
      )

    case AddRuntimeConfig(reqId, config) =>
      val id = ProfileId(config.name, config.version)
      findConfig(id) match {
        case None =>
          // TODO: stage

          val dir = new File(new File(installBaseDir, config.name), config.version)
          dir.mkdirs()

          val confFile = new File(dir, "profile.conf")

          ConfigWriter.write(RuntimeConfig.toConfig(config), confFile, None)
          profiles += id -> Profile(dir, config, Profile.Pending(Seq("Never checked")))

          sender() ! RuntimeConfigAdded(reqId)
        case Some(`config`) =>
          sender() ! RuntimeConfigAdded(reqId)
        case Some(collision) =>
          sender() ! RuntimeConfigAdditionFailed(reqId, "A different runtime config is already present under the same coordinates")
      }

    case StageNextRuntimeConfig(reqId) =>
      if (stagingInProgress.isEmpty) {
        profiles.collect {
          case (id, Profile(_, _, Profile.Pending(_))) =>
            log.info("About to auto-stage profile {}", id)
            self ! StageRuntimeConfig(nextId(), id.name, id.version)
        }
      }

    case StageRuntimeConfig(reqId, name, version) =>
      profiles.get(ProfileId(name, version)) match {
        case None =>
          sender() ! RuntimeConfigStagingFailed(reqId, "No such runtime configuration found")

        case Some(Profile(dir, config, Profile.Valid)) =>
          // already staged
          sender() ! RuntimeConfigStaged(reqId)

        case Some(Profile(installDir, config, state)) =>
          val reqActor = sender()
          if (stagingInProgress.contains(reqId)) {
            log.error("Duplicate id detected. Dropping request: {}", msg)
          } else {
            log.info("About to stage installation: {}", config)

            // analyze config
            val bundles = config.framework :: config.bundles.toList
            // determine missing artifacts
            val (existing, missing) = bundles.partition(b => new File(installDir, b.jarName).exists())
            // download artifacts
            val missingWithId = missing.map { missingBundle =>
              val inProgress = BundleInProgress(nextId(), missingBundle, new File(installDir, missingBundle.jarName))
              artifactDownloader ! Download(inProgress.reqId, self, missingBundle.url, inProgress.file)
              inProgress
            }
            // check artifacts
            val existingWithId = existing.map { existingBundle =>
              val inProgress = BundleInProgress(nextId(), existingBundle, new File(installDir, existingBundle.jarName))
              artifactChecker ! CheckFile(inProgress.reqId, self, inProgress.file, existingBundle.sha1Sum)
              inProgress
            }
            stageInProgress(State(reqId, reqActor, config, installDir, missingWithId, existingWithId, Seq()))
          }

      }

    case ActivateRuntimeConfig(reqId, name: String, version: String) =>
      val requestingActor = sender()
      profiles.get(ProfileId(name, version)) match {
        case Some(Profile(dir, config, Profile.Valid)) =>
          // write config
          val launcherConfig = ConfigConverter.runtimeConfigToLauncherConfig(config, installBaseDir.getPath())
          log.debug("About to activate launcher config: {}", launcherConfig)
          launchConfigSetter(launcherConfig)
          requestingActor ! RuntimeConfigActivated(reqId)
          restartFramework()
        case _ =>
          sender() ! RuntimeConfigActivationFailed(reqId, "No such staged runtime configuration found")
      }

    case GetProgress(reqId) =>
      stagingInProgress.get(reqId) match {
        case Some(state) => sender ! Progress(reqId, state.progress())
        case None => sender() ! UnknownRequestId(reqId)
      }

  }

  override def receive: Actor.Receive = LoggingReceive {
    case p: Protocol => protocol(p)

    case DownloadFinished(downloadId, url, file) =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.bundlesToDownload.find { bip => bip.reqId == downloadId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}. Url: {}", downloadId, url)
        case (state, bundleInProgress) :: _ =>
          val newToCheck = bundleInProgress.copy(reqId = nextId())
          artifactChecker ! CheckFile(newToCheck.reqId, self, newToCheck.file, newToCheck.bundle.sha1Sum)
          stageInProgress(state.copy(
            bundlesToDownload = state.bundlesToDownload.filter(bundleInProgress != _),
            bundlesToCheck = newToCheck +: state.bundlesToCheck
          ))
      }

    case DownloadFailed(downloadId, url, file, error) =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.bundlesToDownload.find { bip => bip.reqId == downloadId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}. Url: {}", downloadId, url)
        case (state, bundleInProgress) :: _ =>
          stageInProgress(state.copy(
            bundlesToDownload = state.bundlesToDownload.filter(bundleInProgress != _),
            issues = error.getMessage() +: state.issues
          ))
      }
      
    case ValidChecksum(checkId, file, sha1Sum) =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.bundlesToCheck.find { bip => bip.reqId == checkId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}. file: {}", checkId, file)
        case (state, bundleInProgress) :: _ =>
          stageInProgress(state.copy(
            bundlesToCheck = state.bundlesToCheck.filter(bundleInProgress != _)
          ))
      }

    case InvalidChecksum(checkId, file, sha1Sum) =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.bundlesToCheck.find { bip => bip.reqId == checkId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}. file: {}", checkId, file)
        case (state, bundleInProgress) :: _ =>
          stageInProgress(state.copy(
            bundlesToCheck = state.bundlesToCheck.filter(bundleInProgress != _),
            issues = s"Invalid checksum for file ${file}" +: state.issues
          ))
      }

  }

}
