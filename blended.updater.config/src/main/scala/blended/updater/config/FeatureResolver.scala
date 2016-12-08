package blended.updater.config

import java.io.File

import com.typesafe.config.{ConfigFactory, ConfigParseOptions}

import scala.util.Try

object FeatureResolver {

  class ResolveContext(features: Seq[FeatureConfig], mvnBaseUrl: Option[String] = None) {

    private[this] var cache: Seq[FeatureConfig] = features

    def fetchFeature(feature: FeatureRef): Option[FeatureConfig] = cache.find(c => c.name == feature.name && c.version == feature.version).orElse {
      feature.url match {
        case None => None
        case Some(unresolveUrl) if mvnBaseUrl.isDefined =>
          Try {
            val url = RuntimeConfig.resolveBundleUrl(unresolveUrl, mvnBaseUrl).get
            val file = File.createTempFile(RuntimeConfig.resolveFileName(url).get, "")
            RuntimeConfigCompanion.download(url, file).get
            val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
            file.delete()
            FeatureConfigCompanion.read(config).get
          }.toOption.map { fetched =>
            synchronized {
              cache ++= Seq(fetched)
              fetched
            }
          }
      }
    }
  }

  sealed trait Fragment
  final case class Unresolved(fragmentRef: FeatureRef) extends Fragment
  final case class Resolved(fragment: FeatureConfig) extends Fragment {
    def fragementRef = FeatureRef(fragment.name, fragment.version, fragment.url)
  }

  def resolve(feature: FeatureRef, context: ResolveContext): Try[Seq[FeatureConfig]] = Try {
    context.fetchFeature(feature) match {
      case Some(fetchedFeature) => Seq(fetchedFeature) ++ fetchedFeature.features.flatMap(f => resolve(f, context).get)
      case None => sys.error("Could not resolve feature: " + feature)
    }
  }

  def resolve(runtimeConfig: RuntimeConfig, features: Seq[FeatureConfig]): Try[ResolvedRuntimeConfig] = Try {
    val context = new ResolveContext(runtimeConfig.resolvedFeatures ++ features, runtimeConfig.mvnBaseUrl)
    ResolvedRuntimeConfig(runtimeConfig, runtimeConfig.features.flatMap(f => resolve(f, context).get))
  }
}