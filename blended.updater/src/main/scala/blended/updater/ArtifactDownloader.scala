package blended.updater

import java.io.File

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import blended.updater.config.{Artifact, RuntimeConfig, RuntimeConfigCompanion}
import blended.updater.config.MvnGav
import blended.util.logging.Logger

class ArtifactDownloader(mvnRepositories : List[String])
  extends Actor
  with ActorLogging {
  import ArtifactDownloader._

  private[this] val log = Logger[ArtifactDownloader]

  def receive : Actor.Receive = LoggingReceive {

    case Download(reqId, artifact, file) =>

      val url = artifact.url
      val urls : Try[List[String]] =
        if (url.startsWith(RuntimeConfig.MvnPrefix)) {
          log.debug(s"detected Maven url: ${url}")
          val gav = MvnGav.parse(artifact.url.substring(RuntimeConfig.MvnPrefix.length()))
          gav.map(gav => mvnRepositories.map(repo => gav.toUrl(repo)))
        } else Success(List(url))

      def fileIssue() : Option[String] = {
        if (!file.exists()) Some(s"File does not exist: ${file}")
        else artifact.sha1Sum match {
          case None => None
          case Some(sha1) => RuntimeConfigCompanion.digestFile(file) match {
            case Some(`sha1`)   => None
            case Some(fileSha1) => Some(s"File checksum ${fileSha1} does not match ${sha1}")
            case None           => Some(s"Chould not verify checksum of file ${file}")
          }
        }
      }

      fileIssue() match {
        case None =>
          sender() ! DownloadFinished(reqId)
        case Some(_) =>
          urls match {
            case Success(Nil) =>
              log.error("No urls or no Maven repositories defined")
              sender() ! DownloadFailed(reqId, s"Could not download file ${file}. Error: No Maven repositories defined.")
            case Success(url :: _) =>
              RuntimeConfigCompanion.download(url, file) match {
                case Success(f) =>
                  fileIssue() match {
                    case None        => sender() ! DownloadFinished(reqId)
                    case Some(issue) => sender() ! DownloadFailed(reqId, issue)
                  }
                case Failure(e) =>
                  log.error(e)(s"Could not download file ${file} from ${url}")
                  sender() ! DownloadFailed(reqId, s"Could not download file ${file} from ${url}. Error: ${e.getMessage()}")
              }
            case Failure(e) =>
              log.error(e)(s"Could not download file ${file}")
              sender() ! DownloadFailed(reqId, s"Could not download file ${file}. Error: ${e.getMessage()}")
          }
      }
  }
}

object ArtifactDownloader {

  // Messages
  sealed trait Protocol {
    def requestId : String
  }
  case class Download(override val requestId : String, artifact : Artifact, file : File) extends Protocol

  // Replies
  sealed trait Reply {
    def requestId : String
  }
  final case class DownloadFinished(requestId : String) extends Reply
  final case class DownloadFailed(requestId : String, error : String) extends Reply

  def props(mvnRepositories : List[String] = List()) : Props = Props(new ArtifactDownloader(mvnRepositories))

}
