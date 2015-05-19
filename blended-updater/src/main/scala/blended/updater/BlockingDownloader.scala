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

object BlockingDownloader {

  // Messages
  case class Download(requestRef: ActorRef, url: String, file: File)

  // Replies
  case class DownloadResult(url: String, file: Try[File])

  /**
   * @param useParentAsSender Use the parent actor as sender, which is useful, when this actor is used via a [akka.routing.Router].
   */
  def props(useParentAsSender: Boolean = false): Props = Props(new BlockingDownloader(useParentAsSender))

}

class BlockingDownloader(useParentAsSender: Boolean) extends Actor with ActorLogging {
  import BlockingDownloader._

  if (useParentAsSender) {
    // replies will go back to parent
    sender().tell("reply", context.parent)
  }

  def receive: Actor.Receive = LoggingReceive {
    case Download(requestRef, url, file) =>
      val result = Try {
        import sys.process._
        file.getParentFile match {
          case null =>
          case parent => if (!parent.exists()) parent.mkdirs()
        }
        new URL(url).#>(file).!
      }.flatMap {
        case 0 => Success(file)
        case retVal => Failure(new RuntimeException(s"Download of ${url} errored with exit value ${retVal}"))
      }
      requestRef ! DownloadResult(url, result)
  }
}