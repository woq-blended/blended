package blended.aws.s3.internal

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CustomHeader
import blended.aws.s3.S3DownloadParams

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import AwsHelpers._

final case class S3RequestSupport(params: S3DownloadParams) {

  private[s3] val contentTypeHeader: String = "Content-Type"
  private[s3] val awsAlgorithm: String = "AWS4-HMAC-SHA256"
  private[s3] val date = new Date()

  private[s3] val dayOnly = {
    val sdf = new SimpleDateFormat("YYYYMMdd")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(date)
  }

  private[s3] val fullDate = {
    val sdf = new SimpleDateFormat("YYYYMMdd'T'HHmmss'Z'")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(date)
  }

  private[s3] val s3Host: String = s"${params.bucket}.${params.service}.${params.region}.${params.provider}"
  private[s3] val s3Path: String = if (params.path.startsWith("/")) params.path.substring(1) else params.path
  private[s3] val s3Url: String = s"https://$s3Host/${s3Path}"

  private[s3] val trimall: String => String = _.trim.replaceAll(" [ ]+", " ")

  private[s3] val httpHeader = Map(
    "Host" -> s3Host,
    contentTypeHeader -> "application/octet-stream",
    "x-amz-date" -> fullDate.format(date),
    "x-amz-content-sha256" -> "UNSIGNED-PAYLOAD"
  )

  private[s3] lazy val authHeader =
    s"$awsAlgorithm Credential=${params.s3Key}/$credentialsScope, SignedHeaders=$signedHeaders, Signature=$signature"

  // Content-Type and Host are added implicitly by Akka HTTP
  private[s3] lazy val akkaHttpHeader: Seq[HttpHeader] = httpHeader
    .filter(_._1.startsWith("x-amz"))
    .map { case (k, v) =>
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

  private[s3] val canHeader =
    httpHeader.toList
      .sortBy(_._1.toLowerCase)
      .map { case (k, v) => (k.toLowerCase, trimall(v)) }

  private[s3] val signedHeaders = canHeader.map((_._1)).mkString(";")

  private[s3] val canonicalRequest: String = {

    val lines: Seq[String] =
      Seq(
        "GET", // The HTTP method
        s"/$s3Path", // the path element of the request
        "" // Query String is empty
      ) ++
        canHeader.map { case (k, v) => s"$k:$v" } ++
        Seq(
          "",
          signedHeaders,
          "UNSIGNED-PAYLOAD"
        )

    lines.mkString("\n")
  }

  private[s3] lazy val credentialsScope = s"$dayOnly/${params.region}/${params.service}/aws4_request"

  private[s3] lazy val toSign: String = Seq(
    awsAlgorithm,
    fullDate,
    credentialsScope,
    hexEncode(hash(canonicalRequest.getBytes("UTF-8")))
  ).mkString("\n")

  private[s3] lazy val signKey: Array[Byte] = {
    val kDate = hmacSHA256(("AWS4" + params.s3SecretKey).getBytes("UTF-8"), dayOnly)
    val kRegion = hmacSHA256(kDate, params.region)
    val kService = hmacSHA256(kRegion, params.service)
    hmacSHA256(kService, "aws4_request")
  }

  private[s3] lazy val signature: String = hexEncode(hmacSHA256(signKey, toSign))

  private[s3] lazy val authHeaderValue =
    s"$awsAlgorithm Credential=${params.s3Key}/$credentialsScope, SignedHeaders=$signedHeaders, Signature=$signature"

}
