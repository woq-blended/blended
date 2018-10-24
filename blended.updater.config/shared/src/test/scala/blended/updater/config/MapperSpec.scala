package blended.updater.config

import scala.util.Success

import org.scalatest.FreeSpec

class MapperSpec extends FreeSpec {

  "Mapper maps and unmaps to identity" - {

    import Mapper._

    val artifacts = Seq(
      Artifact(url = "http://fake/url"),
      Artifact(url = "http://fake/url/with/md5sum", fileName = "artifact.jar"),
      Artifact(url = "http://fake/url/with/md5sum", sha1Sum = "123456"),
      Artifact(url = "http://fake/url/with/md5sum", fileName = "artifact.jar", sha1Sum = "123456")
    )

    "Artifact" in {
      artifacts.foreach { artifact =>
        assert(unmapArtifact(mapArtifact(artifact)) === Success(artifact))
      }
    }

    val bundleConfigs = artifacts.flatMap(artifact => Seq(
      BundleConfig(artifact = artifact, start = false, startLevel = None),
      BundleConfig(artifact = artifact, start = false, startLevel = Some(5)),
      BundleConfig(artifact = artifact, start = true, startLevel = None),
      BundleConfig(artifact = artifact, start = true, startLevel = Some(5))
    ))

    "BundleConfig" in {
      bundleConfigs.foreach { bundleConfig =>
        assert(unmapBundleConfig(mapBundleConfig(bundleConfig)) === Success(bundleConfig))
      }
    }

    val featureRefs = Seq(
      FeatureRef(name = "name", version = "1.0.0", url = None),
      FeatureRef(name = "name", version = "1.0.0", url = Some("http://fake/url")),
    )

    "FeatureRef" in {
      featureRefs.foreach { featureRef =>
        assert(unmapFeatureRef(mapFeatureRef(featureRef)) === Success(featureRef))
      }
    }

    val featureConfigs = Seq(None, Some("http://fake.url/")).flatMap { url =>
      Seq(List(), List(FeatureRef("a", "1"))).flatMap { features =>
        Seq(List(), bundleConfigs.toList).flatMap { bundles =>
          Seq(FeatureConfig(name = "name", version = "1.0.0", url = url, bundles = bundles, features = features))
        }
      }
    }
    "FeatureConfig" in {
      featureConfigs.foreach { featureConfig =>
        assert(unmapFeatureConfig(mapFeatureConfig(featureConfig)) === Success(featureConfig))
      }
      // TODO: cases with bundles and features
    }

    "RuntimeConfig" in pending
    "RemoteContainerState" in pending
    "ServiceInfo" in pending
    "ContainerInfo" in pending
    "UpdateAction" in pending
    "OverlayConfig" in pending
    "GeneratedConfig" in pending
    "Profile" in pending
    "OverlaySet" in pending
    "OverlayRef" in pending
  }

}
