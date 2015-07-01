package blended.updater

import java.io.File
import java.net.URL
import scala.sys.process.urlToProcess
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import akka.actor.ActorRef
import akka.actor.Props
import scala.util.control.NonFatal
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import blended.updater.config.RuntimeConfig

object BlockingDownloader {

  // Messages
  case class Download(reqId: String, requestRef: ActorRef, url: String, file: File)

  // Replies
  sealed trait DownloadReply {
    def reqId: String
  }
  final case class DownloadFinished(reqId: String, url: String, file: File) extends DownloadReply
  final case class DownloadFailed(reqId: String, url: String, file: File, error: Throwable) extends DownloadReply

  def props(): Props = Props(new BlockingDownloader())

}

class BlockingDownloader() extends Actor with ActorLogging {
  import BlockingDownloader._

  def receive: Actor.Receive = LoggingReceive {
    case Download(reqId, requestRef, url, file) =>
      RuntimeConfig.download(url, file) match {
        case Success(f) =>
          requestRef ! DownloadFinished(reqId, url, file)
        case Failure(e) =>
          requestRef ! DownloadFailed(reqId, url, file, e)
      }
  }
}