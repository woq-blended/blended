package blended.updater

import java.io.File
import java.util.UUID
import scala.collection.immutable._
import org.osgi.framework.BundleContext
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
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
import blended.launcher.LauncherConfig
import blended.launcher.LauncherConfigRepository

object Updater {

  sealed trait Protocol {
    def requestId: String
  }
  trait Reply

  /**
   * Request lists of runtime configurations. Replied with [RuntimeConfigs].
   */
  case class GetRuntimeConfigs(requestId: String) extends Protocol
  case class RuntimeConfigs(requestId: String, unstaged: Seq[RuntimeConfig], staged: Seq[RuntimeConfig]) extends Reply

  case class AddRuntimeConfig(requestId: String, runtimeConfig: RuntimeConfig) extends Protocol
  case class RuntimeConfigAdded(requestId: String) extends Reply
  case class RuntimeConfigAdditionFailed(requestId: String, reason: String) extends Reply

  // explicit trigger staging of a config, but idea is to automatically stage not already staged configs when idle
  case class StageRuntimeConfig(requestId: String, name: String, version: String) extends Protocol
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
    configDir: String,
    baseDir: File,
    unstagedConfigRepository: RuntimeConfigRepository,
    stagedConfigRepository: RuntimeConfigRepository,
    launcherConfigRepository: LauncherConfigRepository,
    restartFramework: () => Unit,
    artifactDownloaderProps: Props = null,
    artifactCheckerProps: Props = null): Props =
    Props(new Updater(
      configDir,
      baseDir,
      unstagedConfigRepository,
      stagedConfigRepository,
      launcherConfigRepository,
      restartFramework,
      Option(artifactDownloaderProps),
      Option(artifactCheckerProps)
    ))

  /**
   * A bundle in progress, e.g. downloading or verifying.
   */
  private case class BundleInProgress(reqId: String, bundle: BundleConfig, file: File)

  /**
   * Internal working state of in-progress stagings.
   */
  private case class State(
      requestId: String,
      requestActor: ActorRef,
      config: RuntimeConfig,
      installDir: File,
      bundlesToDownload: Seq[BundleInProgress],
      bundlesToCheck: Seq[BundleInProgress]) {

    def progress(): Int = {
      val allBundlesSize = config.bundles.size
      if (allBundlesSize > 0)
        (100 / allBundlesSize) * (allBundlesSize - bundlesToDownload.size - bundlesToCheck.size)
      else 100
    }

  }

}

class Updater(
  configDir: String,
  installBaseDir: File,
  unstagedConfigRepo: RuntimeConfigRepository,
  stagedConfigRepo: RuntimeConfigRepository,
  launchConfigRepo: LauncherConfigRepository,
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

  private[this] def stageInProgress(state: State): Unit = {
    val id = state.requestId
    val config = state.config
    val progress = state.progress()
    log.debug("Progress: {} for reqestId: {}", progress, id)

    if (state.bundlesToCheck.isEmpty && state.bundlesToDownload.isEmpty) {
      stagingInProgress = stagingInProgress.filterKeys(id != _)
      stagedConfigRepo.add(config)
      unstagedConfigRepo.remove(config.name, config.version)
      state.requestActor ! RuntimeConfigStaged(id)
    } else {
      stagingInProgress += id -> state
    }
  }

  def findConfig(name: String, version: String): Option[RuntimeConfig] = unstagedConfigRepo.getByNameAndVersion(name, version)
    .orElse(stagedConfigRepo.getByNameAndVersion(name, version))

  private[this] def nextId(): String = UUID.randomUUID().toString()

  def protocol(msg: Protocol): Unit = msg match {

    case GetRuntimeConfigs(reqId) =>
      sender() ! RuntimeConfigs(reqId,
        unstaged = unstagedConfigRepo.getAll(),
        staged = stagedConfigRepo.getAll()
      )

    case AddRuntimeConfig(reqId, config) =>
      findConfig(config.name, config.version) match {
        case None =>
          unstagedConfigRepo.add(config)
          sender() ! RuntimeConfigAdded(reqId)
        case Some(`config`) =>
          sender() ! RuntimeConfigAdded(reqId)
        case Some(collision) =>
          sender() ! RuntimeConfigAdditionFailed(reqId, "A different runtime config is already present under the same coordinates")
      }

    case StageRuntimeConfig(reqId, name, version) =>
      stagedConfigRepo.getByNameAndVersion(name, version) match {
        case Some(config) =>
          // already staged
          sender() ! RuntimeConfigStaged(reqId)
        case None =>
          unstagedConfigRepo.getByNameAndVersion(name, version) match {
            case Some(config) =>

              val reqActor = sender()
              if (stagingInProgress.contains(reqId)) {
                log.error("Duplicate id detected. Dropping request: {}", msg)
              } else {
                log.info("About to stage installation: {}", config)

                val installDir = new File(installBaseDir, s"${config.name}-${config.version}")
                if (installDir.exists()) {
                  log.debug("Installation directory already exists: {}", installDir)
                }

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
                stageInProgress(State(reqId, reqActor, config, installDir, missingWithId, existingWithId))
              }
            case None =>
              sender() ! RuntimeConfigStagingFailed(reqId, "No such runtime configuration found")
          }
      }

    case ActivateRuntimeConfig(reqId, name: String, version: String) =>
      val requestingActor = sender()
      stagedConfigRepo.getByNameAndVersion(name, version) match {
        case None =>
          sender() ! RuntimeConfigActivationFailed(reqId, "No such runtime configuration found")
        case Some(config) =>
          // write config
          val launcherConfig = ConfigConverter.convertToLauncherConfig(config, installBaseDir.getPath())
          log.debug("About to activate launcher config: {}", launcherConfig)
          launchConfigRepo.updateConfig(launcherConfig)
          requestingActor ! RuntimeConfigActivated(reqId)
          restartFramework()
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
      val foundState = stagingInProgress.values.find { state =>
        state.bundlesToDownload.find { bip => bip.reqId == downloadId }.isDefined
      }
      foundState match {
        case None =>
          log.error("Unkown download id {}. Url: {}", downloadId, url)
        case Some(state) =>
          log.debug("Cancelling in progress state: {}\nReason: {}", state, error)
          stagingInProgress = stagingInProgress.filterKeys(state.requestId != _)
          state.requestActor ! RuntimeConfigStagingFailed(state.requestId, error.getMessage())
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
          val errorMsg = "Invalid checksum for resource from URL: " + bundleInProgress.bundle.url
          log.debug("Cancelling in progress state: {}\nReason: Invalid checksum", state)
          stagingInProgress = stagingInProgress.filterKeys(state.requestId != _)
          state.requestActor ! RuntimeConfigStagingFailed(state.requestId, errorMsg)
      }

  }

}
