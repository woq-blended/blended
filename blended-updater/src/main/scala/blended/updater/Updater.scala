package blended.updater

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.event.LoggingReceive
import akka.routing.BalancingPool
import blended.launcher.config.LauncherConfig
import blended.updater.config.Artifact
import blended.updater.config.BundleConfig
import blended.updater.config.ConfigConverter
import blended.updater.config.ConfigWriter
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import org.osgi.framework.BundleContext
import scala.collection.immutable._
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.typesafe.config.Config

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
  sealed trait Reply

  /**
   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
   */
  final case class GetRuntimeConfigs(requestId: String) extends Protocol
  final case class RuntimeConfigs(requestId: String, staged: Seq[RuntimeConfig], pending: Seq[RuntimeConfig], invalid: Seq[RuntimeConfig]) extends Reply

  final case class AddRuntimeConfig(requestId: String, runtimeConfig: RuntimeConfig) extends Protocol
  final case class RuntimeConfigAdded(requestId: String) extends Reply
  final case class RuntimeConfigAdditionFailed(requestId: String, reason: String) extends Reply

  final case class ScanForRuntimeConfigs(requestId: String) extends Protocol

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  final case class StageRuntimeConfig(requestId: String, name: String, version: String) extends Protocol
  final case class StageNextRuntimeConfig(requestId: String) extends Protocol
  final case class RuntimeConfigStaged(requestId: String) extends Reply
  final case class RuntimeConfigStagingFailed(requestId: String, reason: String) extends Reply

  final case class ActivateRuntimeConfig(requestId: String, name: String, version: String) extends Protocol
  final case class RuntimeConfigActivated(requestId: String) extends Reply
  final case class RuntimeConfigActivationFailed(requestId: String, reason: String) extends Reply

  final case class GetProgress(requestId: String) extends Protocol
  final case class Progress(requestId: String, progress: Int) extends Reply

  final case class UnknownRuntimeConfig(requestId: String) extends Reply
  final case class UnknownRequestId(requestId: String) extends Reply

  def props(
    baseDir: File,
    profileUpdater: (String, String) => Boolean,
    restartFramework: () => Unit,
    artifactDownloaderProps: Props = null,
    artifactCheckerProps: Props = null,
    unpackerProps: Props = null,
    config: UpdaterConfig): Props = {

    Props(new Updater(
      installBaseDir = baseDir,
      profileUpdater = profileUpdater,
      restartFramework = restartFramework,
      Option(artifactDownloaderProps),
      Option(artifactCheckerProps),
      Option(unpackerProps),
      config
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
      config: RuntimeConfig,
      installDir: File,
      artifactsToDownload: Seq[ArtifactInProgress],
      artifactsToCheck: Seq[ArtifactInProgress],
      pendingArtifactsToUnpack: Seq[ArtifactInProgress],
      artifactsToUnpack: Seq[ArtifactInProgress],
      issues: Seq[String]) {

    val profileId = ProfileId(config.name, config.version)

    def progress(): Int = {
      val allBundlesSize = config.bundles.size
      if (allBundlesSize > 0)
        (100 / allBundlesSize) * (allBundlesSize - artifactsToDownload.size - artifactsToCheck.size)
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

class Updater(
  installBaseDir: File,
  profileUpdater: (String, String) => Boolean,
  restartFramework: () => Unit,
  artifactDownloaderProps: Option[Props],
  artifactCheckerProps: Option[Props],
  unpackerProps: Option[Props],
  config: UpdaterConfig)
    extends Actor
    with ActorLogging {
  import Updater._

  val artifactDownloader = context.actorOf(
    artifactDownloaderProps.getOrElse(BalancingPool(config.artifactDownloaderPoolSize).props(BlockingDownloader.props())),
    "artifactDownloader")
  val artifactChecker = context.actorOf(
    artifactCheckerProps.getOrElse(BalancingPool(config.artifactCheckerPoolSize).props(Sha1SumChecker.props())),
    "artifactChecker")
  val unpacker = context.actorOf(
    unpackerProps.getOrElse(BalancingPool(config.unpackerPoolSize).props(Unpacker.props())),
    "unpacker")

  // requestId -> State
  private[this] var stagingInProgress: Map[String, State] = Map()

  private[this] var profiles: Map[ProfileId, Profile] = Map()

  private[this] var stageProfilesTicker: Option[Cancellable] = None

  // TODO: generate properties file
  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

    if (state.artifactsToCheck.isEmpty && state.artifactsToDownload.isEmpty && state.issues.isEmpty && !state.pendingArtifactsToUnpack.isEmpty) {
      // start unpacking
      state.pendingArtifactsToUnpack.foreach { a =>
        unpacker ! Unpacker.Unpack(a.reqId, self, a.file, state.installDir)
      }
      stageInProgress(state.copy(
        artifactsToUnpack = state.pendingArtifactsToUnpack,
        pendingArtifactsToUnpack = Nil
      ))
    } else if (state.artifactsToCheck.isEmpty && state.artifactsToDownload.isEmpty && state.artifactsToUnpack.isEmpty) {
      // no work left
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

    if (config.autoStagingIntervalMSec > 0) {
      log.info(s"Enabling auto-staging with interval [${config.autoStagingIntervalMSec}] and initial delay [${config.autoStagingDelayMSec}]")
      implicit val eCtx = context.system.dispatcher
      stageProfilesTicker = Some(context.system.scheduler.schedule(
        Duration(config.autoStagingDelayMSec, TimeUnit.MILLISECONDS),
        Duration(config.autoStagingIntervalMSec, TimeUnit.MILLISECONDS)) {
          self ! StageNextRuntimeConfig(nextId())
        })
    }

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
        Try {
          val config = ConfigFactory.parseFile(profileFile).resolve()
          val runtimeConfig = RuntimeConfig.read(config).get
          if (runtimeConfig.name == name && runtimeConfig.version == version) {
            val issues = RuntimeConfig.validate(
              versionDir,
              runtimeConfig,
              includeResourceArchives = false,
              explodedResourceArchives = true)
            log.debug(s"Validation result for [${name}-${version}]: ${issues.mkString(";")}")
            val profileState = issues match {
              case Seq() => Profile.Valid
              case issues => Profile.Pending(issues)
            }
            List(Profile(versionDir, runtimeConfig, profileState))
          } else List()
        }.getOrElse(List())
      }

      profiles = foundProfiles.map { profile => profile.profile -> profile }.toMap

    case GetRuntimeConfigs(reqId) =>
      sender() ! RuntimeConfigs(reqId,
        staged = profiles.values.toList.collect { case Profile(_, config, Profile.Valid) => config },
        pending = profiles.values.toList.collect { case Profile(_, config, Profile.Pending(_)) => config },
        invalid = profiles.values.toList.collect { case Profile(_, config, Profile.Invalid(_)) => config }
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
        profiles.toStream.collect {
          case (id, Profile(_, _, Profile.Pending(_))) =>
            log.info("About to auto-stage profile {}", id)
            self ! StageRuntimeConfig(nextId(), id.name, id.version)
        }.headOption
      }

    case StageRuntimeConfig(reqId, name, version) =>
      profiles.get(ProfileId(name, version)) match {
        case None =>
          sender() ! RuntimeConfigStagingFailed(reqId, s"No such runtime configuration found: ${name} ${version}")

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

            val artifacts = config.allBundles.map { b =>
              ArtifactInProgress(nextId(), b.artifact, config.bundleLocation(b, installDir))
            } ++
              config.resources.map { r =>
                ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r, installDir))
              }

            val pendingUnpacks = config.resources.map { r =>
              ArtifactInProgress(nextId(), r, config.resourceArchiveLocation(r, installDir))
            }

            val (existing, missing) = artifacts.partition(a => a.file.exists())

            val missingWithId = missing.map { a =>
              val resolvedUrl = config.resolveBundleUrl(a.artifact.url).getOrElse(a.artifact.url)
              artifactDownloader ! BlockingDownloader.Download(a.reqId, self, resolvedUrl, a.file)
              a
            }
            val existingWithId = existing.filter(_.artifact.sha1Sum.isDefined).map { a =>
              artifactChecker ! Sha1SumChecker.CheckFile(a.reqId, self, a.file, a.artifact.sha1Sum.get)
              a
            }

            stageInProgress(State(
              requestId = reqId,
              requestActor = reqActor,
              config = config,
              installDir = installDir,
              artifactsToDownload = missingWithId,
              artifactsToCheck = existingWithId,
              pendingArtifactsToUnpack = pendingUnpacks,
              artifactsToUnpack = Seq(),
              issues = Seq()))
          }

      }

    case ActivateRuntimeConfig(reqId, name: String, version: String) =>
      val requestingActor = sender()
      profiles.get(ProfileId(name, version)) match {
        case Some(Profile(dir, config, Profile.Valid)) =>
          // write config
          log.debug("About to activate new profile for next startup: {}-{}", name, version)
          val success = profileUpdater(name, version)
          if (success) {
            requestingActor ! RuntimeConfigActivated(reqId)
            restartFramework()
          } else {
            requestingActor ! RuntimeConfigActivationFailed(reqId, "Could not update next startup profile")
          }
        case _ =>
          requestingActor ! RuntimeConfigActivationFailed(reqId, "No such staged runtime configuration found")
      }

    case GetProgress(reqId) =>
      stagingInProgress.get(reqId) match {
        case Some(state) => sender ! Progress(reqId, state.progress())
        case None => sender() ! UnknownRequestId(reqId)
      }

  }

  override def receive: Actor.Receive = LoggingReceive {
    case p: Protocol => protocol(p)

    case msg: BlockingDownloader.DownloadReply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToDownload.find { bip => bip.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}", msg.reqId)
        case (state, bundleInProgress) :: _ =>
          val artifactsToDownload = state.artifactsToDownload.filter(bundleInProgress != _)
          msg match {
            case BlockingDownloader.DownloadFinished(_, url, file) =>
              val toCheck = bundleInProgress.artifact.sha1Sum.map { sha1Sum =>
                // only check if we have a checksum
                val newToCheck = bundleInProgress.copy(reqId = nextId())
                artifactChecker ! Sha1SumChecker.CheckFile(newToCheck.reqId, self, newToCheck.file, sha1Sum)
                newToCheck
              }
              stageInProgress(state.copy(
                artifactsToDownload = artifactsToDownload,
                artifactsToCheck = toCheck.toList ++: state.artifactsToCheck
              ))
            case BlockingDownloader.DownloadFailed(_, url, file, error) =>
              stageInProgress(state.copy(
                artifactsToDownload = artifactsToDownload,
                issues = s"Download failed for ${file} (${error.getClass().getSimpleName()}: ${error.getMessage()})" +: state.issues
              ))
          }
      }

    case msg: Sha1SumChecker.Reply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToCheck.find { bip => bip.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}", msg.reqId)
        case (state, bundleInProgress) :: _ =>
          val artifactsToCheck = state.artifactsToCheck.filter(bundleInProgress != _)
          msg match {
            case Sha1SumChecker.ValidChecksum(_, _, _) =>
              stageInProgress(state.copy(
                artifactsToCheck = artifactsToCheck
              ))
            case Sha1SumChecker.InvalidChecksum(_, file, sha1Sum) =>
              stageInProgress(state.copy(
                artifactsToCheck = artifactsToCheck,
                issues = s"Invalid checksum for file ${file} (expected: ${bundleInProgress.artifact.sha1Sum}, found: ${sha1Sum})" +: state.issues
              ))
          }
      }

    case msg: Unpacker.UnpackReply =>
      val foundProgress = stagingInProgress.values.flatMap { state =>
        state.artifactsToUnpack.find { a => a.reqId == msg.reqId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown unpack process {}", msg.reqId)
        case (state, artifact) :: _ =>
          val artifactsToUnpack = state.artifactsToUnpack.filter(artifact != _)
          msg match {
            case Unpacker.UnpackingFinished(_) =>
              state.config.createResourceArchiveTouchFile(artifact.artifact, artifact.artifact.sha1Sum, state.installDir) match {
                case Success(file) =>
                  stageInProgress(state.copy(
                    artifactsToUnpack = artifactsToUnpack
                  ))
                case Failure(e) =>
                  stageInProgress(state.copy(
                    artifactsToUnpack = artifactsToUnpack,
                    issues = s"Could not create unpacked-marker file for resource file ${artifact.file} (${e.getClass().getSimpleName()}: ${e.getMessage()})" +: state.issues
                  ))
              }
            case Unpacker.UnpackingFailed(_, e) =>
              stageInProgress(state.copy(
                artifactsToUnpack = artifactsToUnpack,
                issues = s"Could not unpack file ${artifact.file} (${e.getClass().getSimpleName()}: ${e.getMessage()})" +: state.issues
              ))
          }
      }

  }

}
