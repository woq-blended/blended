package blended.updater

import java.io.File

import scala.util.Failure
import scala.util.Success

import org.slf4j.LoggerFactory

import ArtifactDownloader.DownloadFailed
import ArtifactDownloader.DownloadFinished
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import blended.updater.config.Artifact
import blended.updater.config.RuntimeConfig

class ArtifactDownloader()
    extends Actor
    with ActorLogging {
  import ArtifactDownloader._

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactDownloader])

  def receive: Actor.Receive = LoggingReceive {

    case Download(reqId, artifact, file) =>

      val url = artifact.url

      def fileOk(): Boolean = artifact.sha1Sum match {
        case None => file.exists()
        case Some(sha1) => file.exists() && (RuntimeConfig.digestFile(file) == sha1)
      }

      if (fileOk()) {
        sender() ! DownloadFinished(reqId)
      } else {
        RuntimeConfig.download(url, file) match {
          case Success(f) =>
            if (fileOk()) {
              sender() ! DownloadFinished(reqId)
            } else {
              sender() ! DownloadFailed(reqId, "Invalid checksum")
            }
          case Failure(e) =>
            log.error("Could not download file {} from {}", file, url, e)
            sender() ! DownloadFailed(reqId, s"Could not download file ${file} from ${url}. Error: ${e.getMessage()}")
        }
      }
  }
}

object ArtifactDownloader {

  // Messages
  sealed trait Protocol {
    def requestId: String
  }
  case class Download(override val requestId: String, artifact: Artifact, file: File) extends Protocol

  // Replies
  sealed trait Reply {
    def requestId: String
  }
  final case class DownloadFinished(requestId: String) extends Reply
  final case class DownloadFailed(requestId: String, error: String) extends Reply

  def props(): Props = Props(new ArtifactDownloader())

}
