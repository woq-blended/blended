package blended.updater.config

import java.io.File

import blended.updater.config.util.DownloadHelper
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}

import scala.util.Try
import blended.updater.config.util.Unzipper

class FeatureResolver(featureDir : File, features : Seq[FeatureConfig] = Seq.empty, mvnBaseUrl : Option[String] = None) {

  private class ResolveContext {

    // This is the cache of already loaded feature configs
    private[this] var cache: Map[String, Map[String, FeatureConfig]] = features.groupBy(_.repoUrl).map{ case (url, cfs) =>
      (url -> cfs.map(fc => fc.name -> fc).toMap)
    }

    features.map{ fc =>
      ((fc.repoUrl, fc.name) -> fc)
    }.toMap

    private def fromCache(ref : FeatureRef) : Try[Option[Seq[FeatureConfig]]] = Try {
      cache.get(ref.url) match {
        // The feature has not yet been resolved
        case None => None
        // The feature has already been resolved, make sure, all names are present
        case Some(m) =>
          val unresolved : Seq[String] = ref.names.filter(n => !m.isDefinedAt(n))
          if (unresolved.isEmpty) {
            Some(m.values.toSeq)
          } else {
            throw new Exception(s"Could not resolve [${unresolved.mkString(",")}] from url [${ref.url}]")
          }
      }
    }

    private def updateCache(url : String) : Try[Seq[FeatureConfig]] = Try {

      if (!featureDir.exists()) {
        featureDir.mkdirs()
      }

      val fileName : String = Profile.resolveFileName(url).get
      val downloaded : File = DownloadHelper.download(url, File.createTempFile(fileName, "repo", featureDir)).get

      val unzipped = Unzipper.unzip(
        archive = downloaded,
        targetDir = new File(featureDir, fileName)
      ).get

      val fcs : Seq[FeatureConfig] = unzipped.collect{
        case f if f.isFile() && f.canRead() && f.getName().endsWith(".conf") =>
          val config = ConfigFactory.parseFile(f, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          FeatureConfigCompanion.read(config).get
      }

      require(fcs.map(_.repoUrl).distinct.size == 1)
      require(fcs.map(_.repoUrl).head == url)

      cache += (url -> fcs.map(fc => fc.name -> fc).toMap)

      fcs
    }

    def fetchFeature(ref: FeatureRef): Try[Seq[FeatureConfig]] = Try {
      fromCache(ref).get match {
        case Some(s) => s
        case None => updateCache(ref.url).get
      }
    }
  }

  private val resolveContext : ResolveContext = new(ResolveContext)

  /**
   * Resolve a single feature reference.
   * @param feature
   * @return
   */
  def resolve(feature: FeatureRef): Try[Seq[FeatureConfig]] = resolveContext.fetchFeature(feature)

  def resolve(profile: Profile): Try[ResolvedProfile] = Try {
    val resolved : List[FeatureConfig] = profile.features.flatMap{ f => 
      resolveContext.fetchFeature(f).get
    }

    ResolvedProfile(profile.copy(resolvedFeatures = resolved))
  }
}
