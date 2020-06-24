package blended.updater.config

/**
 * Encapsulates a [[Profile]] guaranteed to contain resolved [FeatureConfig]s for each contained (transitive) [[FeatureRef]].
 *
 * If there are unresolved (transitive) features, this class construction throws with a [[java.lang.IllegalArgumentException]].
 *
 * @see [[FeatureResolver]] for a way to automatically resolve features, e.g. from remote repositories.
 *
 */
case class ResolvedProfile(profile: Profile) {

  {
    // // Check if all feature reference have a according resolved feature
    // def check(features: List[FeatureRef], depChain: List[String]): Unit = features.foreach { f =>
    //   val depName = s"${f.name}-${f.version}"
    //   val newDepChain = depName :: depChain
    //   require(
    //     depChain.find(_ == depName).isEmpty,
    //     s"No cycles in feature dependencies allowed, but detected: ${newDepChain.mkString(" required by ")}")
    //   val feature = lookupFeature(f)
    //   require(
    //     feature.isDefined,
    //     s"Contains resolved feature: ${newDepChain.mkString(" required by ")}. Known resolved features are: ${profile.resolvedFeatures
    //       .map(f => s"${f.name}-${f.version}")
    //       .distinct
    //       .mkString(",")}"
    //   )
    //   check(feature.get.features, newDepChain)
    // }
    // check(profile.features, s"${profile.name}-${profile.version}" :: Nil)

    // force evaluation of framework, which throws if invalid
    // framework

    // check, that features do not conflict
    //var seen = Set[(String, String)]()
    
    val conflicts = profile.features
    // profile.features.flatMap { f =>
    //   val key = f.name -> f.version
    //   if (seen.contains(key)) Some(s"${f.name}-${f.version}")
    //   else {
    //     seen += key
    //     None
    //   }
    // }

    require(
      conflicts.isEmpty,
      s"Contains no conflicting resolved features. Multiple features detected: ${conflicts.mkString(", ")}.")

  }

  def lookupFeature(featureRef: FeatureRef): Option[FeatureConfig] = {
    //(profile.resolvedFeatures).find(f => f.name == featureRef.name && f.version == featureRef.version)
    None
  }

  /**
   * All referenced features.
   */
  def allReferencedFeatures: List[FeatureConfig] = {
    def find(features: List[FeatureRef]): List[FeatureConfig] = features.flatMap { f =>
      val feature = lookupFeature(f).get
      feature +: find(feature.features)
    }
    find(profile.features).distinct
  }

  /**
   * All bundles of this runtime config including those transitively defined in the features.
   */
  def allBundles: List[BundleConfig] = (profile.bundles ++ allReferencedFeatures.flatMap(_.bundles)).distinct

  val framework: BundleConfig = {
    val fs = allBundles.filter(b => b.startLevel == Some(0))
    require(
      fs.distinct.size == 1,
      s"A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0', but this one has (distinct): ${fs.size}${if (fs.isEmpty) ""
      else fs.mkString("\n  ", "\n  ", "")}"
    )
    fs.head
  }
}

object ResolvedProfile extends (Profile => ResolvedProfile) {

  /**
   * Construct with additional resolved features.
   */
  def apply(runtimeConfig: Profile, features: List[FeatureConfig]): ResolvedProfile = {

    val allFeatures = (runtimeConfig.resolvedFeatures ++ features).distinct

    ResolvedProfile(
      runtimeConfig.copy(resolvedFeatures = allFeatures)
    )
  }
}
