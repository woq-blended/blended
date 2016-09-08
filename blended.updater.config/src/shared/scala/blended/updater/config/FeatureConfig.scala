package blended.updater.config

import scala.collection.immutable.Seq

case class FeatureConfig(
  name: String,
  version: String,
  url: Option[String],
  bundles: Seq[BundleConfig],
  features: Seq[FeatureRef]) {

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},url=${url},bundles=${bundles},features=${features})"

  def featureRef: FeatureRef = FeatureRef(name = name, version = version, url = url)
}

object FeatureConfig extends ((String, String, Option[String], Seq[BundleConfig], Seq[FeatureRef]) => FeatureConfig) {
  def apply(name: String,
    version: String,
    url: String = null,
    bundles: Seq[BundleConfig] = null,
    features: Seq[FeatureRef] = null
  ): FeatureConfig = {
    FeatureConfig(
      name = name,
      version = version,
      url = Option(url),
      bundles = Option(bundles).getOrElse(Seq()),
      features = Option(features).getOrElse(Seq())
    )
  }
}

