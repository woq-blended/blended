package blended.updater.config

import java.io.File

import scala.util.Try

/**
 * Encapsulates a [[Profile]] guaranteed to contain resolved [FeatureConfig]s for each contained (transitive) [[FeatureRef]].
 *
 * If there are unresolved (transitive) features, this class construction throws a [[java.lang.IllegalArgumentException]].
 *
 * @see [[FeatureResolver]] for a way to automatically resolve features, e.g. from remote repositories.
 *
 * @param profile: The profile that shall be resolved
 * @param featureDir : a directory where downloaded feature files will be stored
 */
case class ResolvedProfile(profile: Profile, featureDir : File) {

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

  /**
   * Lookup a set of features that belong to the same repository URL
   * @param featureRef : The FeatureRefefence encapsulating the repoUrl and the names of features to be looked up
   * @return Success(s), where s is the sequence of FeatureConfig objects and has one entry for each unique
   *         value within featureRef.names
   *         Failure(t) When notall features could be resolved within the featureRef
   */
  def lookupFeatures(featureRef: FeatureRef): Try[List[FeatureConfig]] = Try {

    val candidates : List[FeatureConfig] =
      profile.resolvedFeatures.filter(fc => fc.repoUrl == featureRef.url && featureRef.names.contains(fc.name))

    val resolvedNames : List[String] = candidates.map(_.name)

    featureRef.names.filter(n => !resolvedNames.contains(n)) match {
      case Nil => candidates.distinct
      case u => throw new Exception(s"Could not resolve [${u.mkString(",")}] for the repo url [${featureRef.url}]")
    }
  }

  /**
   * The complete list of all referenced features
   */
  def allReferencedFeatures: Try[List[FeatureConfig]] = {

    def find(features: List[FeatureRef]): Try[List[FeatureConfig]] = Try {
      val directFeatures : List[FeatureConfig] = features.flatMap(f => lookupFeatures(f).get)
      val transitiveRefs : List[FeatureRef] = directFeatures.flatMap(_.features).distinct
      (directFeatures ++ (find(transitiveRefs)).get).distinct
    }

    find(profile.features)
  }

  /**
   * All bundles of this runtime config including those transitively defined in the features.
   */
  def allBundles: Try[List[BundleConfig]] = Try {
    (profile.bundles ++ allReferencedFeatures.get.flatMap(_.bundles)).distinct
  }

  val framework: Try[BundleConfig] = Try {
    val fs = allBundles.get.filter(b => b.startLevel == Some(0))
    require(
      fs.distinct.size == 1,
      s"A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0', but this one has (distinct): ${fs.size}${if (fs.isEmpty) ""
      else fs.mkString("\n  ", "\n  ", "")}"
    )
    fs.head
  }
}

object ResolvedProfile {

  /**
   * Construct with additional resolved features.
   */
  def apply(profile: Profile, featureDir : File, features: List[FeatureConfig]): ResolvedProfile = {

    val allFeatures = (profile.resolvedFeatures ++ features).distinct

    ResolvedProfile(
      profile = profile.copy(resolvedFeatures = allFeatures),
      featureDir = featureDir
    )
  }
}
