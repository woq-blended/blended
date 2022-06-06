package blended.aws.s3.internal

import blended.aws.s3._

import java.io.File

object AwsS3DownloaderImpl {

  def make(cfg: AwsS3Config) : AwsS3Downloader = new AwsS3Downloader {
    override def download(s3Url: String, keyId: String): Either[Throwable, File] = Left(new Exception("Not implemented!"))
  }

}
