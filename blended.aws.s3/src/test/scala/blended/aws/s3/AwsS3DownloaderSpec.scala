package blended.aws.s3

import akka.actor.ActorSystem
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await

class AwsS3DownloaderSpec extends LoggingFreeSpec with Matchers {

  "The AwsS3Downloader should" - {

    "download a specified file from S3" in {

      val system = ActorSystem("S3Downlaod")
      val downLoader = AwsS3Downloader.make(system)

      val params = S3DownloadParams(
        bucket = "woq-kl-test",
        region = "eu-central-1",
        provider = "amazonaws.com",
        path = "replace.txt",
        s3Key = "AKIAQMPD3ETE3GFSQ6O5",
        s3SecretKey = "HF78nDJumjpebLY/a13q+w3XK05ryoLshXvBTRZn"
      )

      val res = downLoader.download(params)

      val bytes = Await.result(res, params.timeout)
      println(new String(bytes))
    }
  }

}
