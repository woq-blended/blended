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
  sealed trait Protocol {
    def reqId: String
  }
  case class Download(override val reqId: String, url: String, file: File) extends Protocol

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
    case Download(reqId, url, file) =>
      RuntimeConfig.download(url, file) match {
        case Success(f) =>
          sender() ! DownloadFinished(reqId, url, file)
        case Failure(e) =>
          sender() ! DownloadFailed(reqId, url, file, e)
      }
  }
}