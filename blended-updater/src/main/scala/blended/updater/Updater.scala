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

  // Messages
  /**
   * Request a list of staged runtime configurations. Replied with [StagedUpdates].
   */
  case class GetStagedUpdates(requestId: String, requestActor: ActorRef)

  /**
   * Stage a runtime configuration. Replied with:
   * - [StageUpdateProgress] to indicate progress
   * - [StageUpdateFinished] to indicate success
   * - [StageUpdateCancelled] to indicate failure
   */
  case class StageUpdate(requestId: String, requestActor: ActorRef, config: RuntimeConfig)

  case class ActivateStage(requestId: String, requestActor: ActorRef, stageName: String, stageVersion: String)

  // Replies
  /**
   * Contains a list of staged runtime configurations. Reply to [GetStagedUpdates]
   */
  case class StagedUpdates(requestId: String, configs: Seq[RuntimeConfig])

  case class StageUpdateProgress(requestId: String, progress: Int)

  case class StageUpdateCancelled(requestId: String, reason: Throwable)

  case class StageUpdateFinished(requestTd: String)

  case class StageActivated(requestId: String)

  case class StageActivationFailed(requestId: String, reason: String)

  def props(
    configDir: String,
    baseDir: File,
    runtimeConfigRepository: RuntimeConfigRepository,
    launcherConfigRepository: LauncherConfigRepository,
    restartFramework: () => Unit): Props =
    Props(new Updater(configDir, baseDir, runtimeConfigRepository, launcherConfigRepository, restartFramework))

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
    bundlesToCheck: Seq[BundleInProgress])

}

class Updater(
  configDir: String,
  installBaseDir: File,
  runtimeConfigRepo: RuntimeConfigRepository,
  launchConfigRepo: LauncherConfigRepository,
  restartFramework: () => Unit)
    extends Actor
    with ActorLogging {
  import Updater._

  // FIXME: move magic numbers into config
  val artifactDownloader = context.actorOf(BalancingPool(4).props(BlockingDownloader.props()), "artifactDownloader")
  val artifactChecker = context.actorOf(BalancingPool(4).props(Sha1SumChecker.props()), "artifactChecker")

  private[this] var inProgress: Map[String, State] = Map()

  private[this] def updateInProgress(state: State): Unit = {
    val id = state.requestId

    val allBundlesSize = 1 + state.config.bundles.size
    val progress = (100 / allBundlesSize) * (allBundlesSize - state.bundlesToDownload.size - state.bundlesToCheck.size)
    log.debug("Progress: {} for reqestId: {}", progress, id)
    state.requestActor ! StageUpdateProgress(id, progress)

    if (state.bundlesToCheck.isEmpty && state.bundlesToDownload.isEmpty) {
      inProgress = inProgress.filterKeys(id != _)
      runtimeConfigRepo.add(state.config)
      state.requestActor ! StageUpdateFinished(id)
    } else {
      inProgress += id -> state
    }
  }

  private[this] def nextId(): String = UUID.randomUUID().toString()

  override def receive: Actor.Receive = LoggingReceive {
    case GetStagedUpdates(reqId, reqRef) =>
      reqRef ! StagedUpdates(reqId, runtimeConfigRepo.getAll())

    case msg @ StageUpdate(reqId, reqActor, config) =>
      if (inProgress.contains(reqId)) {
        log.error("Duplicate id detected. Dropping request: {}", msg)
      }

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
      updateInProgress(State(reqId, reqActor, config, installDir, missingWithId, existingWithId))

    case DownloadFinished(downloadId, url, file) =>
      val foundProgress = inProgress.values.flatMap { state =>
        state.bundlesToDownload.find { bip => bip.reqId == downloadId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown download id {}. Url: {}" + downloadId, url)
        case (state, bundleInProgress) :: _ =>
          val newToCheck = bundleInProgress.copy(reqId = nextId())
          artifactChecker ! CheckFile(newToCheck.reqId, self, newToCheck.file, newToCheck.bundle.sha1Sum)
          updateInProgress(state.copy(
            bundlesToDownload = state.bundlesToDownload.filter(bundleInProgress != _),
            bundlesToCheck = newToCheck +: state.bundlesToCheck
          ))
      }

    case DownloadFailed(downloadId, url, file, error) =>
      val foundState = inProgress.values.find { state =>
        state.bundlesToDownload.find { bip => bip.reqId == downloadId }.isDefined
      }
      foundState match {
        case None =>
          log.error("Unkown download id {}. Url: {}" + downloadId, url)
        case Some(state) =>
          log.debug("Cancelling in progress state: {}\nReason: {}", state, error)
          inProgress = inProgress.filterKeys(state.requestId != _)
          state.requestActor ! StageUpdateCancelled(state.requestId, error)
      }

    case ValidChecksum(checkId, file, sha1Sum) =>
      val foundProgress = inProgress.values.flatMap { state =>
        state.bundlesToCheck.find { bip => bip.reqId == checkId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}. file: {}" + checkId, file)
        case (state, bundleInProgress) :: _ =>
          updateInProgress(state.copy(
            bundlesToCheck = state.bundlesToCheck.filter(bundleInProgress != _)
          ))
      }

    case InvalidChecksum(checkId, file, sha1Sum) =>
      val foundProgress = inProgress.values.flatMap { state =>
        state.bundlesToCheck.find { bip => bip.reqId == checkId }.map(state -> _).toList
      }.toList
      foundProgress match {
        case Nil =>
          log.error("Unkown check id {}. file: {}" + checkId, file)
        case (state, bundleInProgress) :: _ =>
          val errorMsg = "Invalid checksum for resource from URL: " + bundleInProgress.bundle.url
          log.debug("Cancelling in progress state: {}\nReason: Invalid checksum", state)
          inProgress = inProgress.filterKeys(state.requestId != _)
          state.requestActor ! StageUpdateCancelled(state.requestId, new RuntimeException(errorMsg))
      }

    case ActivateStage(requestId, requestingActor, stageName, stageVersion) =>
      runtimeConfigRepo.getByNameAndVersion(stageName, stageVersion) match {
        case None =>
          requestingActor ! StageActivationFailed(requestId, "Stage not found")
        case Some(runtimeConfig) =>
          // write config
          val launcherConfig = ConfigConverter.convertToLauncherConfig(runtimeConfig, installBaseDir.getPath())
          log.debug("About to activate launcher config: {}", launcherConfig)
          launchConfigRepo.updateConfig(launcherConfig)
          requestingActor ! StageActivated(requestId)
          restartFramework()
      }

  }

}
