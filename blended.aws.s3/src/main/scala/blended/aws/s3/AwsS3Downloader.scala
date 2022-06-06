package blended.aws.s3

import java.io.File

trait AwsS3Downloader {

  /**
   * Download a file from S3 to a temporary file that can be processed afterwards.
   * Either returns an error thrown by the download or a handle to the downloaded file.
   */
  def download(s3Url: String, keyId: String) : Either[Throwable, File]

}
