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

        val outStream = new BufferedOutputStream(new FileOutputStream(file))
        try {

          val connection = new URL(url).openConnection
          connection.setRequestProperty("User-Agent", "Blended Updater")
          val inStream = new BufferedInputStream(connection.getInputStream())
          try {
            val bufferSize = 1024
            var break = false
            var len = 0
            var buffer = new Array[Byte](bufferSize)

            while (!break) {
              inStream.read(buffer, 0, bufferSize) match {
                case x if x < 0 => break = true
                case count => {
                  len = len + count
                  outStream.write(buffer, 0, count)
                }
              }
            }
          } finally {
            inStream.close()
          }
        } finally {
          outStream.flush()
          outStream.close()
        }
        requestRef ! DownloadFinished(reqId, url, file)
      } catch {
        case NonFatal(e) =>
          requestRef ! DownloadFailed(reqId, url, file, e)
      }
  }
}