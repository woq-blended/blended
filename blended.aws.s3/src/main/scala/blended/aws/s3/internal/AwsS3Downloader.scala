package blended.aws.s3.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import blended.util.logging.Logger
import akka.http.scaladsl.model._
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import AWSHelpers._

// https://woq-kl-test.s3.eu-central-1.amazonaws.com/replace.txt

final class AwsS3Downloader(system: ActorSystem) {

  implicit val eCtxt : ExecutionContext = system.dispatcher
  private val logger = Logger("blended.aws.s3.internal.AwsS3Downloader")

  /**
   * Download a file from S3 to a byte Array that can be processed further.
   * Either returns an error thrown by the download or the content of the downloaded file.
   */
  def download(params: S3DownloadParams): Future[Array[Byte]] =  {

    implicit val materializer: Materializer =
      akka.stream.SystemMaterializer.get(system).materializer

    logger.info(s"Trying to download ${params.s3Url}")

    val req = HttpRequest(HttpMethods.GET, params.s3Url)
      .mapEntity(_.withContentType(ContentTypes.`application/octet-stream`))
      .withHeaders(params.akkaHttpHeader.toSeq)

    val resp = Http()(system).singleRequest(req)

    logger.debug(s"Byte encoded canonical download request: (${hexEncode(params.canonicalRequest.getBytes("UTF-8"), " ")})")
    logger.debug(s"Byte encoded meta data: (${hexEncode(params.toSign.getBytes("UTF-8"), " ")})")

    resp.flatMap(_.entity.toStrict(params.timeout)).map(_.data.toArray[Byte])
  }
}
