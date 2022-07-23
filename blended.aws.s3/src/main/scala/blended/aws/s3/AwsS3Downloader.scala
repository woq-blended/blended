package blended.aws.s3

import akka.actor.ActorSystem
import blended.aws.s3.internal.AwsS3DownloaderImpl

import scala.concurrent.Future

trait AwsS3Downloader {
  def download(params: S3DownloadParams): Future[Array[Byte]]
}

object AwsS3Downloader {
  def make(system: ActorSystem) : AwsS3Downloader = new AwsS3DownloaderImpl(system)
}
