package blended.updater.config

/**
 * A downloadable resource file with optional checksum.
 */
case class Artifact(
  url : String,
  fileName : Option[String],
  sha1Sum : Option[String]
) {

  override def toString() : String = s"${getClass().getSimpleName()}(url=$url,fileName=$fileName,sha1Sum=$sha1Sum)"

}

object Artifact extends ((String, Option[String], Option[String]) => Artifact) {
  def apply(
    url : String,
    // scalastyle:off null
    fileName : String = null,
    sha1Sum : String = null
  // scalastyle:on null
  ) : Artifact = {
    Artifact(
      url = url,
      fileName = Option(fileName),
      sha1Sum = Option(sha1Sum)
    )
  }
}
