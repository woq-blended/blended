package blended.updater.config

/**
 * Encapsulated a [RuntimeConfig] guaranteed to contain resolved [FeatureConfig]s for each contained (transitive) [FreatureRef].
 *
 * If there are unresolved (transitive) features, this class construction throws with a [java.lang.IllegalArgumentException].
 *
 * @see [FeatureResolver] for a way to automatically resolve features, e.g. from remote repositories.
 *
 */
case class ResolvedRuntimeConfig(runtimeConfig: RuntimeConfig) {

  {
    // Check if all feature reference have a according resolved feature
    def check(features: List[FeatureRef], depChain: List[String]): Unit = features.foreach { f =>
      val depName = s"${f.name}-${f.version}"
      val newDepChain = depName :: depChain
      require(depChain.find(_ == depName).isEmpty, s"No cycles in feature dependencies allowed, but detected: ${newDepChain.mkString(" required by ")}")
      val feature = lookupFeature(f)
      require(feature.isDefined, s"Contains resolved feature: ${newDepChain.mkString(" required by ")}. Known resolved features are: ${runtimeConfig.resolvedFeatures.map(f => s"${f.name}-${f.version}").distinct.mkString(",")}")
      check(feature.get.features, newDepChain)
    }
    check(runtimeConfig.features, s"${runtimeConfig.name}-${runtimeConfig.version}" :: Nil)

    // force evaluation of framework, which throws if invalid
    // framework

    // check, that features do not conflict
    var seen = Set[(String, String)]()
    val conflicts = runtimeConfig.features.flatMap { f =>
      val key = f.name -> f.version
      if (seen.contains(key)) Some(s"${f.name}-${f.version}")
      else {
        seen += key
        None
      }
    }
    require(conflicts.isEmpty, s"Contains no conflicting resolved features. Multiple features detected: ${conflicts.mkString(", ")}.")

  }

  def lookupFeature(featureRef: FeatureRef): Option[FeatureConfig] = {
    (runtimeConfig.resolvedFeatures).find(f => f.name == featureRef.name && f.version == featureRef.version)
  }

  /**
   * All referenced features.
   */
  def allReferencedFeatures: List[FeatureConfig] = {
    def find(features: List[FeatureRef]): List[FeatureConfig] = features.flatMap { f =>
      val feature = lookupFeature(f).get
      feature +: find(feature.features)
    }
    find(runtimeConfig.features).distinct
  }

  /**
   * All bundles of this runtime config including those trasitively defined in the features.
   */
  def allBundles: List[BundleConfig] = (runtimeConfig.bundles ++ allReferencedFeatures.flatMap(_.bundles)).distinct

  val framework: BundleConfig = {
    val fs = allBundles.filter(b => b.startLevel == Some(0))
    require(fs.distinct.size == 1, s"A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0', but this one has (distinct): ${fs.size}${
      if (fs.isEmpty) "" else fs.mkString("\n  ", "\n  ", "")
    }")
    fs.head
  }
}

object ResolvedRuntimeConfig extends (RuntimeConfig => ResolvedRuntimeConfig) {

  /**
   * Construct with additional resolved features.
   */
  def apply(runtimeConfig: RuntimeConfig, features: List[FeatureConfig]): ResolvedRuntimeConfig = {

    val allFeatures = (runtimeConfig.resolvedFeatures ++ features).distinct

    ResolvedRuntimeConfig(
      runtimeConfig.copy(resolvedFeatures = allFeatures)
    )
  }
}