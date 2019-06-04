package blended.updater.config

/**
 * A bundle with a start configuration.
 * Used as part of [[RuntimeConfig]] oder [[FeatureConfig]].
 *
 * @param artifact The artifact (file).
 * @param start `true` if the bundle should be auto-started on container start.
 * @param startLevel The start level of this bundle.
 * @see [[RuntimeConfig]]
 * @see [[FeatureConfig]]
 */
case class BundleConfig(
  artifact : Artifact,
  start : Boolean,
  startLevel : Option[Int]
) {

  def url : String = artifact.url

  def jarName : Option[String] = artifact.fileName

  def sha1Sum : Option[String] = artifact.sha1Sum

  override def toString() : String = s"${getClass().getSimpleName()}(artifact=${artifact},start=${start},url=${url},startLevel=${startLevel})"
}

object BundleConfig extends ((Artifact, Boolean, Option[Int]) => BundleConfig) {

  def apply(
    url : String,
    jarName : String = null,
    sha1Sum : String = null,
    start : Boolean = false,
    startLevel : Integer = null
  ) : BundleConfig =
    BundleConfig(
      artifact = Artifact(fileName = Option(jarName), url = url, sha1Sum = Option(sha1Sum)),
      start = start,
      startLevel = if (startLevel != null) Some(startLevel.intValue()) else None
    )
}
