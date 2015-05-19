package blended.updater

import scala.collection.immutable.Seq
import org.osgi.framework.BundleContext
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import blended.updater.internal.Logger
import blended.updater.unused.RuntimeConfigDiff
import akka.actor.ActorRef
import java.io.File
import akka.routing.BalancingPool
import akka.event.LoggingReceive
import blended.updater.BlockingDownloader.DownloadResult
import blended.updater.BlockingDownloader.Download
import scala.util.Failure
import scala.util.Success
import blended.updater.Sha1SumChecker.CheckFile
import blended.updater.Sha1SumChecker.ValidChecksum
import blended.updater.Sha1SumChecker.InvalidChecksum
import blended.updater.BlockingDownloader.DownloadResult
import blended.updater.Sha1SumChecker.InvalidChecksum

object UpdaterActor {

  // Messages
  //  case class GetCurrentConfig()
  case class Update(requestId: String, requestActor: ActorRef, config: RuntimeConfig)

  // Replies
  case object AnotherUpdateInProgress
  //  case class CurrentConfig(config: RuntimeConfig, diff: Seq[RuntimeConfigDiff] = Seq())
  //  case class InvalidConfigFile(msg: String)
  case class UpdateCancelled(requestId: String, config: RuntimeConfig, reason: Throwable)

  def props(bundleContext: BundleContext, configDir: String, baseDir: File): Props = Props(new UpdaterActor(bundleContext, configDir: String, baseDir: File))

}

class UpdaterActor(bundleContext: BundleContext, configDir: String, baseDir: File)
  extends Actor
  with ActorLogging {
  import UpdaterActor._

  private[this] val log = Logger[UpdaterActor]

  val artifactDownloader = context.actorOf(BalancingPool(5).props(BlockingDownloader.props(false)), "artifactDownloader")
  val artifactChecker = context.actorOf(BalancingPool(5).props(Sha1SumChecker.props(false)), "artifactChecker")

  
  
//  case class State(
//    requestId: String,
//    requestActor: ActorRef,
//    config: RuntimeConfig,
//    installDir: File,
//    bundlesToDownload: Seq[(Long, BundleConfig)],
//    bundlesToCheck: Seq[(Long, BundleConfig)])
//
//  var inProgress: Map[String, State] = Map()

  override def receive: Actor.Receive = idle

  def updateState(reqId: String, requestActor: ActorRef, config: RuntimeConfig, installDir: File, missing: Seq[BundleConfig], toCheck: Seq[BundleConfig]) = {
    if (missing.isEmpty && toCheck.isEmpty) {
      //      context.become(deploying(requestActor, config, installDir), true)

      // UPDATE

    } else {
      context.become(LoggingReceive(downloading(reqId, requestActor, config, installDir, missing, toCheck) orElse notIdle(requestActor)))
    }
  }

  def becomeIdle() {
    context.become(LoggingReceive(idle orElse notDownloading()))
  }

  def idle: Actor.Receive = {
    case Update(reqId, reqActor, config) =>
      val installDir = new File(baseDir, s"${config.name}-${config.version}")

      // analyze config
      val bundles = config.framework :: config.bundles.toList
      // determine missing artifacts
      val (existing, missing) = bundles.partition(b => !new File(installDir, b.jarName).exists())
      // download artifacts
      missing.foreach { missingBundle =>
        artifactDownloader ! Download(self, missingBundle.url, new File(installDir, missingBundle.jarName))
      }
      // check artifacts
      existing.foreach { existingBundle =>
        artifactChecker ! CheckFile(self, new File(installDir, existingBundle.jarName), existingBundle.sha1Sum)
      }
      // generate runtime config
      updateState(reqId, reqActor, config, installDir, missing, existing)

  }

  def notIdle(requestActor: ActorRef): Actor.Receive = {
    case Update(reqId, reqRef, _) => reqRef ! AnotherUpdateInProgress
  }

  def notDownloading(): Actor.Receive = {
    case DownloadResult(_, _) =>
    // not expecting any download, must be old
    case ValidChecksum(_, _) =>
    // not expecting any checksum 
    case InvalidChecksum(_, _) =>
    // not expecting any checksum 
  }

  def downloading(reqId: String, requestActor: ActorRef, config: RuntimeConfig, installDir: File, missing: Seq[BundleConfig], toCheck: Seq[BundleConfig]): Actor.Receive = {
    case DownloadResult(url, fileTry) =>
      fileTry match {
        case Success(file) =>
          missing.find(b => b.url == url && new File(installDir, b.jarName) == file) match {
            case Some(bundle) =>
              val newMissing = missing.filter(bundle !=)
              val newToCheck = bundle +: toCheck
              artifactChecker ! CheckFile(self, new File(installDir, bundle.jarName), bundle.sha1Sum)
              updateState(reqId, requestActor, config, installDir, newMissing, newToCheck)
            case None =>
            // not our bundle
            // FIXME: ignore for now
          }
        case Failure(ex) =>
          requestActor ! UpdateCancelled(reqId, config, ex)
          becomeIdle()
      }
    case ValidChecksum(file, sha1Sum) =>
      toCheck.find(b => new File(installDir, b.jarName) == file && b.sha1Sum == sha1Sum) match {
        case Some(bundle) =>
          val newToCheck = toCheck.filter(bundle !=)
          updateState(reqId, requestActor, config, installDir, missing, newToCheck)
        case None =>
        // not our bundle
        // FIXME: ignore for now
      }
    case InvalidChecksum(file, actualChecksum) =>
      toCheck.find(b => new File(installDir, b.jarName) == file) match {
        case Some(bundle) =>
          requestActor ! UpdateCancelled(reqId, config, new RuntimeException("Invalid checksum"))
          becomeIdle()
        case None =>
        // not our bundle
        // FIXME: ignore for now
      }
  }

  //  def deploying(requestActor: ActorRef, config: RuntimeConfig, installDir: File): Actor.Receive = {
  //    case 
  //  } orElse notIdle(requestActor) orElse notDownloading()

}
