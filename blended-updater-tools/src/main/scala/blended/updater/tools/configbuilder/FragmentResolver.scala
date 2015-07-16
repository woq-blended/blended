package blended.updater.tools.configbuilder

import blended.updater.config.FeatureConfig
import blended.updater.config.RuntimeConfig
import scala.util.Try
import java.io.File
import com.typesafe.config.ConfigFactory

trait FragmentResolver {
  import FragmentResolver._

  def resolveFragments(runtimeConfig: RuntimeConfig, fragments: Seq[FeatureConfig]): Either[Seq[Fragment], Seq[FeatureConfig]] = {

    val requested: Seq[FragmentRef] = runtimeConfig.features.map(f => FragmentRef(f.name, f.version, f.url))

    var resolved: Map[FragmentRef, FeatureConfig] = Map()
    var unresolved: Map[FragmentRef, Seq[FragmentRef]] = Map()
    var unseen: Seq[FeatureConfig] = Seq()

    var cont = true

    while (cont) {

    }

    ???
  }

}

object FragmentResolver {

  case class FragmentRef(name: String, version: String, url: Option[String])

  class ResolveContext(runtimeConfig: RuntimeConfig, features: Seq[FeatureConfig]) {

    private[this] var cache: Map[FragmentRef, FeatureConfig] = Map()

    features.flatMap(flattenFragments).filter(isResolved).foreach { f =>
      cache += FragmentRef(f.name, f.version, f.url) -> f
    }

    def fetchFeature(feature: FragmentRef): Option[FeatureConfig] = cache.get(feature).orElse {
      feature.url match {
        case None => None
        case Some(unresolveUrl) =>
          Try {
            val url = runtimeConfig.resolveBundleUrl(unresolveUrl).get
            val file = File.createTempFile(runtimeConfig.resolveFileName(url).get, "")
            RuntimeConfig.download(url, file).get
            val config = ConfigFactory.parseFile(file).resolve()
            file.delete()
            FeatureConfig.read(config).get
          }.toOption.map { fetched =>
            synchronized {
              cache += feature -> fetched
              fetched
            }
          }
      }
    }
  }

  sealed trait Fragment
  final case class Unresolved(fragmentRef: FragmentRef) extends Fragment
  final case class Resolved(fragment: FeatureConfig) extends Fragment {
    def fragementRef = FragmentRef(fragment.name, fragment.version, fragment.url)
  }

  def isResolved(feature: FeatureConfig): Boolean =
    (!feature.bundles.isEmpty || !feature.features.isEmpty || feature.url.isEmpty)

  def flattenFragments(feature: FeatureConfig): Seq[FeatureConfig] =
    feature +: feature.features.flatMap(flattenFragments)

  def findUnresolved(feature: FeatureConfig): Seq[FeatureConfig] =
    flattenFragments(feature).filterNot(isResolved)

  def resolve(feature: FeatureConfig, context: ResolveContext): Try[FeatureConfig] = Try {
    if (isResolved(feature))
      feature
    else {
      context.fetchFeature(FragmentRef(feature.name, feature.version, feature.url)) match {
        case Some(fetchedFeature) =>
          fetchedFeature.copy(features = fetchedFeature.features.map(f => resolve(f, context).get))
          fetchedFeature
        case None => sys.error("Could not resolve feature: " + feature)
      }
    }
  }

  def resolve(runtimeConfig: RuntimeConfig, features: Seq[FeatureConfig]): Try[RuntimeConfig] = Try {
    val context = new ResolveContext(runtimeConfig, features)
    runtimeConfig.copy(
      features = runtimeConfig.features.map(f => resolve(f, context).get)
    )
  }
}