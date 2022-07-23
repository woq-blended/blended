package blended.aws.s3

import scala.concurrent.duration._

final case class S3DownloadParams(
   bucket: String,
   service: String = "s3",
   region: String,
   provider: String = "amazonaws.com",
   path: String,
   s3Key: String,
   s3SecretKey: String,
   timeout: FiniteDuration = 60.seconds
 )
