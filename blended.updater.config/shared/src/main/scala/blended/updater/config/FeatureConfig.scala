package blended.updater.config

/**
 * A Feature configuration, holds a collection of [[BundleConfig]]s to build up a [[Profile]].
 */
case class FeatureConfig(
  name : String,
  version : String,
  url : Option[String],
  bundles : List[BundleConfig],
  features : List[FeatureRef]
) {

  override def toString() : String = s"${getClass().getSimpleName()}(name=${name},version=${version},url=${url},bundles=${bundles},features=${features})"

  def featureRef : FeatureRef = FeatureRef(name = name, version = version, url = url)
}

object FeatureConfig extends ((String, String, Option[String], List[BundleConfig], List[FeatureRef]) => FeatureConfig) {
  /**
   * Conveniently create a [[FeatureConfig]].
   */
  def apply(
    name : String,
    version : String,
    url : String = null,
    bundles : List[BundleConfig] = null,
    features : List[FeatureRef] = null
  ) : FeatureConfig = {
    FeatureConfig(
      name = name,
      version = version,
      url = Option(url),
      bundles = Option(bundles).getOrElse(List.empty),
      features = Option(features).getOrElse(List.empty)
    )
  }
}

