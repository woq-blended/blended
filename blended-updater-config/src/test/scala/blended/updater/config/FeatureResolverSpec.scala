package blended.updater.config

import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.collection.immutable
import scala.util.Success

class FeatureResolverSpec extends FreeSpec with Matchers {

  val resolver = FeatureResolver

  val feature1 = FeatureConfig(name = "feature1", version = "1")
  val feature2 = FeatureConfig(name = "feature2", version = "1")

  val fullFeature1 = FeatureConfig(name = "feature1", version = "1", bundles = immutable.Seq(BundleConfig(url = "mvn:feature1:bundle1:1")))
  val fullFeature2 = FeatureConfig(
    name = "feature2",
    version = "1",
    bundles = immutable.Seq(BundleConfig(url = "mvn:feature2:bundle1:1")),
    features = immutable.Seq(FeatureConfig(name = "feature3", version = "1"))
  )
  val fullFeature3 = FeatureConfig(
    name = "feature3",
    version = "1",
    bundles = immutable.Seq(BundleConfig(url = "mvn:feature3:bundle1:1"))
  )

  "A unresolved Feature" - {
    "should resolve" in {
      val resolvedTry = resolver.resolve(feature1, new resolver.ResolveContext(Seq(fullFeature1)))
      resolvedTry shouldBe a[Success[_]]
      resolvedTry.get should not equal (feature1)
      resolvedTry.get should equal(fullFeature1)
    }

    "should resolve transitive" in {
      val resolvedTry = resolver.resolve(feature2, new resolver.ResolveContext(Seq(fullFeature1, fullFeature2, fullFeature3)))
      resolvedTry shouldBe a[Success[_]]
      val result = resolvedTry.get
      result should not equal (feature1)
      result.features should have size (1)
      result.allBundles should have size (2)
    }
  }

}
