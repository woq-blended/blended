package blended.aws.s3

import akka.actor.ActorSystem
import blended.aws.s3.internal.AwsS3DownloaderImpl

import scala.concurrent.Future

final case class AWSNotFound(url: String) extends Exception(s"Not found in AWS: [$url]")
final case class AWSUnauthorized(url: String) extends Exception(s"Unauthorized to access: [$url]")

trait AwsS3Downloader {
  def download(params: S3DownloadParams): Future[Array[Byte]]
}

object AwsS3Downloader {
  def make(system: ActorSystem) : AwsS3Downloader = new AwsS3DownloaderImpl(system)
}
