package blended.updater.config

import org.scalatest.{FreeSpec, Matchers}

import scala.util.Success

class FeatureResolverSpec extends FreeSpec with Matchers {

  val resolver = FeatureResolver

  val feature1 = FeatureRef(name = "feature1", version = "1")
  val feature2 = FeatureRef(name = "feature2", version = "1")

  val fullFeature1 = FeatureConfig(name = "feature1", version = "1", bundles = List(BundleConfig(url = "mvn:feature1:bundle1:1")))
  val fullFeature2 = FeatureConfig(
    name = "feature2",
    version = "1",
    bundles = List(BundleConfig(url = "mvn:feature2:bundle1:1")),
    features = List(FeatureRef(name = "feature3", version = "1"))
  )
  val fullFeature3 = FeatureConfig(
    name = "feature3",
    version = "1",
    bundles = List(BundleConfig(url = "mvn:feature3:bundle1:1"))
  )

  "An unresolved Feature" - {
    "should resolve" in {
      val resolvedTry = resolver.resolve(feature1, new resolver.ResolveContext(Seq(fullFeature1)))
      resolvedTry should equal(Success(List(fullFeature1)))
    }

    "should resolve transitive" in {
      val resolvedTry = resolver.resolve(feature2, new resolver.ResolveContext(Seq(fullFeature1, fullFeature2, fullFeature3)))
      resolvedTry shouldBe a[Success[_]]
      val result = resolvedTry.get
      result should have size 2
    }
  }

}
