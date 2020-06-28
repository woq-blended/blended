package blended.updater.config

import scala.util.Try
import scala.util.Success
import scala.util.Failure

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
case class ResolvedProfile(profile: Profile) {

  // Check if all feature reference have a according resolved feature
  private def check(features: List[FeatureRef], depChain: Seq[String]): Try[Unit] = Try {

    features.foreach { f =>
      val singleFeatures : Seq[String] = f.names.map(n => s"${f.url}##$n}")
      val newDepChain : Seq[String] = (singleFeatures ++ depChain).distinct

      val cycledFeatures : Seq[String] = singleFeatures.filter(depChain.contains)

      require(
        cycledFeatures.isEmpty,
        s"No cycles in feature dependencies allowed, but detected cycles for : [${cycledFeatures.mkString(",")}]"
      )

      lookupFeatures(f) match { 
        case Success(l) => 
          check(l.flatMap(_.features), newDepChain)
        case Failure(t) => 
          throw t
      }
    }
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
      val transitiveRefs : List[FeatureConfig] = directFeatures.flatMap(_.features).distinct match {
        case Nil => Nil
        case refs => find(refs).get
      }

      (directFeatures ++ transitiveRefs).distinct
    }

    find(profile.features)
  }

  /**
   * All bundles of this runtime config including those transitively defined in the features.
   */
  def allBundles: Try[List[BundleConfig]] = Try {
    (profile.bundles ++ allReferencedFeatures.get.flatMap(_.bundles)).distinct
  }

  val framework: BundleConfig = {
    val fs = allBundles.get.filter(b => b.startLevel == Some(0))
    require(
      fs.distinct.size == 1,
      s"A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0', but this one has (distinct): ${fs.size}${if (fs.isEmpty) ""
      else fs.mkString("\n  ", "\n  ", "")}"
    )
    fs.head
  }

  require(check(profile.features, Seq.empty).isSuccess)

}

object ResolvedProfile {

  /**
   * Construct with additional resolved features.
   */
  def apply(profile: Profile, features: List[FeatureConfig]): ResolvedProfile = {

    val allFeatures = (profile.resolvedFeatures ++ features).distinct
    ResolvedProfile(profile.copy(resolvedFeatures = allFeatures))
  }
}
