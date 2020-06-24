package blended.updater.config

/**
 * A Feature configuration, holds a collection of [[BundleConfig]]s to build up a [[Profile]].
 */
case class FeatureConfig(
  /** The repo url of the feature repo jar that contains this Feature Config */
  repoUrl : String,
  /** The name of the feature within the repo jar */
  name : String,
  /** The bundles of this feature */
  bundles : List[BundleConfig],
  /** The list of feature references required to load this feature */
  features : List[FeatureRef]
) {

  override def toString() : String = 
    s"""${getClass().getSimpleName()}(" +
       |  repoUrl=${repoUrl}
       |  name=${name}
       |  bundles=${bundles}
       |  features=${features})""".stripMargin
}

object FeatureConfig extends ((String, String, List[BundleConfig], List[FeatureRef]) => FeatureConfig) {
  /**
   * Conveniently create a [[FeatureConfig]].
   */
  def apply(
    repoUrl : String,
    name : String,
    bundles : List[BundleConfig] = List.empty,
    features : List[FeatureRef] = List.empty
  ) : FeatureConfig = {
    FeatureConfig(
      repoUrl = repoUrl,
      name = name,
      bundles = bundles,
      features = features
    )
  }
}

