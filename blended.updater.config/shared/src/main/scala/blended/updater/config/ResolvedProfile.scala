package blended.updater.config

import scala.util.Try

abstract class ResolvedProfileException(s : String) extends Exception(s)

class UnresolvedFeatureException(url : String, unresolved : Seq[String]) extends 
  ResolvedProfileException(s"Could not resolve [${unresolved.mkString(",")}] from [$url]")

class NoFrameworkException extends ResolvedProfileException(s"No framework bundle with startlevel 0 present in config")

class MultipleFrameworksException(bundles : Seq[BundleConfig]) 
  extends ResolvedProfileException(s"Multiple frameworks with startlevel 0 defined in configuration : [${bundles.map(_.artifact).mkString(",")}]")

class CyclicFeatureRefException(cycles: List[FeatureRef])
  extends ResolvedProfileException(s"Cyclic feature reference detected : [${cycles.mkString(",")}]")

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

  /**
   * Lookup a set of features that belong to the same repository URL
   * @param featureRef : The FeatureRefefence encapsulating the repoUrl and the names of features to be looked up
   * @return Success(s), where s is the sequence of FeatureConfig objects and has one entry for each unique
   *         value within featureRef.names
   *         Failure(t) When not all features could be resolved within the featureRef
   */
  def lookupFeatures(featureRef: FeatureRef): Try[List[FeatureConfig]] = Try {

    val candidates : List[FeatureConfig] =
      profile.resolvedFeatures.filter(fc => fc.repoUrl == featureRef.url && featureRef.names.contains(fc.name))

    val resolvedNames : List[String] = candidates.map(_.name)

    featureRef.names.filter(n => !resolvedNames.contains(n)) match {
      case Nil => candidates.distinct
      case u => throw new UnresolvedFeatureException(featureRef.url, u)
    }
  }

  /**
   * The complete list of all referenced features
   */
  def allReferencedFeatures: Try[List[FeatureConfig]] = {

    def find(features: List[FeatureRef], seen : List[FeatureConfig]): Try[List[FeatureConfig]] = Try {

      features match {
        case Nil => seen
        case fl => 
          val directFeatures : List[FeatureConfig] = features.flatMap(f => lookupFeatures(f).get)

          directFeatures.intersect(seen) match {
            case Nil => 
              val transitiveRefs : List[FeatureRef] = directFeatures.flatMap(_.features)
              (directFeatures ++ find(transitiveRefs, (directFeatures ++ seen).distinct).get).distinct
            case cycles => throw new CyclicFeatureRefException(cycles.map(_.toRef))
          }
      }
    }

    find(profile.features, List.empty)
  }

  /**
   * All bundles of this runtime config including those transitively defined in the features.
   */
  def allBundles: Try[List[BundleConfig]] = Try {
    (profile.bundles ++ allReferencedFeatures.get.flatMap(_.bundles)).distinct
  }

  val framework: BundleConfig = {
    allBundles.get.filter(b => b.startLevel == Some(0)) match {
      case Nil => throw new NoFrameworkException
      case h :: Nil => h
      case l => throw new MultipleFrameworksException(l)
    }
  }

  require(allReferencedFeatures.isSuccess)

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
