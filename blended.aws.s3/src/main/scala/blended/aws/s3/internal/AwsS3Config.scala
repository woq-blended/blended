package blended.aws.s3.internal

final case class AwsS3Config(
  s3Key: String,
  s3SecretKey: String,
  resourceType: String,
  dest: String
)

final case class AwsS3ConfigMap(
  vendor: String,
  provider: String,
  routes: Map[String, AwsS3Config]
)
