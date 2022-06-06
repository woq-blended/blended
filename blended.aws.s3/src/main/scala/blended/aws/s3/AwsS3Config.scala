package blended.aws.s3

final case class AwsS3Config private(
  keys: Map[String, String]
)

object AwsS3Config {
  def default : AwsS3Config = AwsS3Config(Map.empty)

  def make(map: Map[String, String]): AwsS3Config = AwsS3Config(map)
}
