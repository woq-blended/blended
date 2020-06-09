package blended.updater

import java.io.File

import akka.actor.{Actor, ActorLogging, Props, actorRef2Scala}
import akka.event.LoggingReceive
import blended.updater.config.{Artifact, MvnGav, Profile, ProfileCompanion}
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}

class ArtifactDownloader(mvnRepositories : List[String])
  extends Actor
  with ActorLogging {
  import ArtifactDownloader._

  private[this] val log = Logger[ArtifactDownloader]

  private def fileIssue(file : File, artifact : Artifact) : Option[String] = {
    if (!file.exists()) {
      Some(s"File does not exist: $file")
    } else {
      artifact.sha1Sum match {
        case None => None
        case Some(sha1) => ProfileCompanion.digestFile(file) match {
          case Some(`sha1`)   => None
          case Some(fileSha1) => Some(s"File checksum $fileSha1 does not match $sha1")
          case None           => Some(s"Could not verify checksum of file $file")
        }
      }
    }
  }

  private def artifactUrls(artifact : Artifact) : Try[List[String]] = {
    val url = artifact.url

    if (url.startsWith(Profile.MvnPrefix)) {
      log.debug(s"detected Maven url: [$url]")
      val gav = MvnGav.parse(artifact.url.substring(Profile.MvnPrefix.length()))
      gav.map(gav => mvnRepositories.map(repo => gav.toUrl(repo)))
    } else {
      Success(List(url))
    }
  }

  private def downloadArtifact(d : Download) : Reply =
    artifactUrls(d.artifact) match {
      case Success(Nil) =>
        log.error("No urls or no Maven repositories defined")
        DownloadFailed(d.requestId, s"Could not download file [${d.file}]. Error: No Maven repositories defined.")

      case Success(url :: _) =>
        ProfileCompanion.download(url, d.file) match {
          case Success(f) =>
            fileIssue(f, d.artifact) match {
              case None        => DownloadFinished(d.requestId)
              case Some(issue) => DownloadFailed(d.requestId, issue)
            }
          case Failure(e) =>
            log.error(e)(s"Could not download file [${d.file}] from [$url]")
            DownloadFailed(d.requestId, s"Could not download file [${d.file}] from [$url]. Error: ${e.getMessage()}")
        }
      case Failure(e) =>
        log.error(e)(s"Could not download file [${d.file}]")
        DownloadFailed(d.requestId, s"Could not download file [${d.file}]. Error: ${e.getMessage()}")
    }

  def receive : Actor.Receive = LoggingReceive {

    case d @ Download(reqId, artifact, file) =>

      fileIssue(file, artifact) match {
        case None =>
          sender() ! DownloadFinished(reqId)

        case Some(_) =>
          sender ! downloadArtifact(d)
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
