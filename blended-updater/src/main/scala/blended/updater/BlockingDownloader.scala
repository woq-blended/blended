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

object BlockingDownloader {

  // Messages
  case class Download(reqId: String, requestRef: ActorRef, url: String, file: File)

  // Replies
  case class DownloadFinished(reqId: String, url: String, file: File)
  case class DownloadFailed(reqId: String, url: String, file: File, error: Throwable)

  /**
   * @param useParentAsSender Use the parent actor as sender, which is useful, when this actor is used via a [akka.routing.Router].
   */
  def props(): Props = Props(new BlockingDownloader())

}

class BlockingDownloader() extends Actor with ActorLogging {
  import BlockingDownloader._

  def receive: Actor.Receive = LoggingReceive {
    case Download(reqId, requestRef, url, file) =>
      try {
        import sys.process._
        file.getParentFile match {
          case null =>
          case parent => if (!parent.exists()) {
            log.debug("Creating dir: {}", parent)
            parent.mkdirs()
          }
        }
        val retVal = new URL(url).#>(file).!
        if (retVal == 0) {
          requestRef ! DownloadFinished(reqId, url, file)
        } else {
          requestRef ! DownloadFailed(reqId, url, file, new RuntimeException(s"Download of ${url} errored with exit value ${retVal}"))
        }
      } catch {
        case NonFatal(e) =>
          requestRef ! DownloadFailed(reqId, url, file, e)
      }
  }
}