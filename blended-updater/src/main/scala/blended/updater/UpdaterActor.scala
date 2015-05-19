package blended.updater

import java.io.File
import java.util.UUID

import scala.collection.immutable.Seq

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

object UpdaterActor {

  // Messages
  case class StageUpdate(requestId: String, requestActor: ActorRef, config: RuntimeConfig)

  // Replies
  case class StageUpdateProgress(requestId: String, progress: Int)
  case class StageUpdateCancelled(requestId: String, reason: Throwable)
  case class StageUpdateFinished(requestid: String)

  def props(bundleContext: BundleContext, configDir: String, baseDir: File): Props = Props(new UpdaterActor(bundleContext, configDir: String, baseDir: File))

  private case class BundleInProgress(reqId: String, bundle: BundleConfig, file: File)

  private case class State(
    requestId: String,
    requestActor: ActorRef,
    config: RuntimeConfig,
    installDir: File,
    bundlesToDownload: Seq[BundleInProgress],
    bundlesToCheck: Seq[BundleInProgress])

}

class UpdaterActor(bundleContext: BundleContext, configDir: String, installBaseDir: File)
  extends Actor
  with ActorLogging {
  import UpdaterActor._

  val artifactDownloader = context.actorOf(BalancingPool(4).props(BlockingDownloader.props()), "artifactDownloader")
  val artifactChecker = context.actorOf(BalancingPool(4).props(Sha1SumChecker.props()), "artifactChecker")

  private[this] var inProgress: Map[String, State] = Map()

  def updateInProgress(state: State): Unit = {
    val id = state.requestId

    val allBundlesSize = 1 + state.config.bundles.size
    val progress = (100 / allBundlesSize) * (allBundlesSize - state.bundlesToDownload.size - state.bundlesToCheck.size)
    log.debug("Progress: {} for reqestId: {}", progress, id)
    state.requestActor ! StageUpdateProgress(id, progress)

    if (state.bundlesToCheck.isEmpty && state.bundlesToDownload.isEmpty) {
      inProgress = inProgress.filterKeys(id !=)
      // TODO: register to stage db
      state.requestActor ! StageUpdateFinished(id)
    } else {
      inProgress += id -> state
    }
  }

  def nextId(): String = UUID.randomUUID().toString()

  override def receive: Actor.Receive = LoggingReceive {
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
            bundlesToDownload = state.bundlesToDownload.filter(bundleInProgress !=),
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
          inProgress = inProgress.filterKeys(state.requestId !=)
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
            bundlesToCheck = state.bundlesToCheck.filter(bundleInProgress !=)
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
          inProgress = inProgress.filterKeys(state.requestId !=)
          state.requestActor ! StageUpdateCancelled(state.requestId, new RuntimeException(errorMsg))
      }
  }

}
