package blended.updater.config

import scala.util.Try

abstract class ResolvedProfileException(s : String) extends Exception(s)

class UnresolvedFeatureException(url : String, unresolved : Seq[String]) extends
  ResolvedProfileException(s"Could not resolve [${unresolved.mkString(",")}] from [$url]")

class NoFrameworkException extends ResolvedProfileException(s"No framework bundle with startlevel 0 present in config")

class MultipleFrameworksException(bundles : Seq[BundleConfig])
  extends ResolvedProfileException(s"Multiple frameworks with startlevel 0 defined in configuration : [${bundles.map(_.artifact).mkString(",")}]")

class CyclicFeatureRefException(cycles: List[FeatureRef])
  extends ResolvedProfileException(s"Cyclic feature reference detected : [${cycles.mkString(" -> ")}]")

/**
 * Encapsulates a [[Profile]] guaranteed to contain resolved [FeatureConfig]s for each contained (transitive) [[FeatureRef]].
 *
 * If there are unresolved (transitive) features, this class construction throws a [[java.lang.IllegalArgumentException]].
 *
 * @see [[FeatureResolver]] for a way to automatically resolve features, e.g. from remote repositories.
 *
 * @param profile: The profile that shall be resolved
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

  def referencedFeatures(f : FeatureConfig, path : List[FeatureConfig]): Try[List[FeatureConfig]] = Try {

    if (path.map(_.repoKey).contains(f.repoKey)) {
      throw new CyclicFeatureRefException((f :: path).reverse.map(_.toRef))
    }

    f :: f.features.flatMap(f => lookupFeatures(f).get)
      .flatMap { dep =>
        referencedFeatures(dep, f :: path).get
      }
  }

  def allReferencedFeatures : Try[List[FeatureConfig]] = Try {
    profile.features.flatMap(fr =>
      lookupFeatures(fr).get
        .flatMap(fc => referencedFeatures(fc, List.empty).get)
    )
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
