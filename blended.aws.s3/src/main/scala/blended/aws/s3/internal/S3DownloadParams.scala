package blended.aws.s3.internal

import AWSHelpers._
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CustomHeader

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import scala.concurrent.duration._

final case class S3DownloadParams(
  bucket: String,
  region: String,
  provider: String = "amazonaws.com",
  path: String,
  s3Key: String,
  s3SecretKey: String,
  timeout: FiniteDuration = 60.seconds
) {
  private val service = "s3"
  private val contentTypeHeader : String = "Content-Type"
  private val awsAlgorithm : String = "AWS4-HMAC-SHA256"
  private val date = new Date()

  private val dayOnly  = {
    val sdf = new SimpleDateFormat("YYYYMMdd")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(date)
  }

  private val fullDate = {
    val sdf = new SimpleDateFormat("YYYYMMdd'T'HHmmss'Z'")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(date)
  }

  val s3Host: String = s"$bucket.$service.$region.$provider"
  val s3Path: String = if (path.startsWith("/")) path.substring(1) else path
  val s3Url: String = s"https://$s3Host/${s3Path}"

  private val trimall : String => String = _.trim.replaceAll(" [ ]+", " ")

  private val httpHeader = Map(
    "Host" -> s3Host,
    contentTypeHeader -> "application/octet-stream",
    "x-amz-date" -> fullDate.format(date),
    "x-amz-content-sha256" -> "UNSIGNED-PAYLOAD"
  )

  private lazy val authHeader =
    s"$awsAlgorithm Credential=$s3Key/$credentialsScope, SignedHeaders=$signedHeaders, Signature=$signature"

  // Content-Type and Host are added implicitly by Akka HTTP
  lazy val akkaHttpHeader : Seq[HttpHeader] = httpHeader
    .filter(_._1.startsWith("x-amz"))
    .map{ case (k,v) =>
      new CustomHeader {
        override def name(): String = k
        override def value(): String = v
        override def renderInRequests(): Boolean = true
        override def renderInResponses(): Boolean = false
      }
    }.toSeq ++ Seq(
      new CustomHeader {
        override def name(): String = "Authorization"
        override def value(): String = authHeader
        override def renderInRequests(): Boolean = true
        override def renderInResponses(): Boolean = false
      }
    )

  private val canHeader =
    httpHeader.toList
      .sortBy(_._1.toLowerCase)
      .map{ case (k,v) => (k.toLowerCase, trimall(v)) }

  private val signedHeaders = canHeader.map((_._1)).mkString(";")

  lazy val canonicalRequest : String = {

    val lines : Seq[String] =
      Seq(
        "GET",                                            // The HTTP method
        s"/$s3Path",                                      // the path element of the request
        ""                                                // Query String is empty
      )  ++
        canHeader.map{ case (k,v) => s"$k:$v" } ++
        Seq(
          "",
          signedHeaders,
          "UNSIGNED-PAYLOAD"
        )

    lines.mkString("\n")
  }

  lazy val credentialsScope = s"$dayOnly/$region/$service/aws4_request"

  lazy val toSign : String = Seq(
    awsAlgorithm,
    fullDate,
    credentialsScope,
    hexEncode(hash(canonicalRequest.getBytes("UTF-8")))
  ).mkString("\n")

  lazy val signKey : Array[Byte] = {
    val kDate = hmacSHA256(("AWS4" + s3SecretKey).getBytes("UTF-8"), dayOnly)
    val kRegion = hmacSHA256(kDate, region)
    val kService = hmacSHA256(kRegion, service)
    hmacSHA256(kService, "aws4_request")
  }

  lazy val signature : String = hexEncode(hmacSHA256(signKey, toSign))

  lazy val authHeaderValue =
    s"$awsAlgorithm Credential=$s3Key/$credentialsScope, SignedHeaders=$signedHeaders, Signature=$signature"
}

