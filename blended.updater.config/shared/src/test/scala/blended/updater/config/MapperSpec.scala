package blended.updater.config

import scala.util.Success

import org.scalatest.FreeSpec

class MapperSpec extends FreeSpec {

  "Mapper maps and unmaps to identity" - {

    import Mapper._

    val artifacts = List(
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

    val bundleConfigs = artifacts.flatMap(artifact => List(
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

    val featureRefs = List(
      FeatureRef(name = "name", version = "1.0.0", url = None),
      FeatureRef(name = "name", version = "1.0.0", url = Some("http://fake/url")),
    )

    "FeatureRef" in {
      featureRefs.foreach { featureRef =>
        assert(unmapFeatureRef(mapFeatureRef(featureRef)) === Success(featureRef))
      }
    }

    val featureConfigs = List(None, Some("http://fake.url/")).flatMap { url =>
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

    val overlayConfigs = Seq(
      OverlayConfig(
        name = "oc",
        version = "1",
        generatedConfigs = List(),
        properties = Map()
      )
      // TODO: add more
    )

    "OverlayConfig" in {
      overlayConfigs.foreach { oc =>
        assert(unmapOverlayConfig(mapOverlayConfig(oc)) === Success(oc))
      }
    }

    val runtimeConfigs = Seq(
      RuntimeConfig(
        name = "rc",
        version = "1",
        bundles = bundleConfigs,
        startLevel = 10,
        defaultStartLevel = 5,
        properties = Map("p1" -> "k1"),
        frameworkProperties = Map("fp1" -> "fk1"),
        systemProperties = Map("sp1" -> "sk1"),
        features = featureRefs,
        resources = List(),
        resolvedFeatures = List()
      )
      // TODO: add more
    )

    "RuntimeConfig" in {
      runtimeConfigs.foreach { rc =>
        assert(unmapRuntimeConfig(mapRuntimeConfig(rc)) === Success(rc))
      }
    }
    "RemoteContainerState" in pending
    "ServiceInfo" in pending
    "ContainerInfo" in pending

    "UpdateAction" in {
      overlayConfigs.foreach { oc =>
        assert(unmapUpdateAction(mapUpdateAction(AddOverlayConfig(oc))) === Success(AddOverlayConfig(oc)))
      }

    }

    "GeneratedConfig" in pending
    "Profile" in pending
    "OverlaySet" in pending
    "OverlayRef" in pending
  }

}
