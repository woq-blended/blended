package blended.updater.config

import scala.util.Success

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FeatureResolverSpec extends AnyFreeSpec with Matchers {

  val resolver = FeatureResolver

  val feature1 = FeatureRef(url = "mvn:group1:repo1:1", names = List("feature1"))
  val feature2 = FeatureRef(url = "mvn:group1:repo2:1", names = List("feature2"))

  val fullFeature1 = FeatureConfig(
    repoUrl = "mvn:group1:repo1:1",
    name = "feature1",
    features = List.empty,
    bundles = List(BundleConfig(url = "mvn:feature1:bundle1:1"))
  )

  val fullFeature2 = FeatureConfig(
    repoUrl = "mvn:group1:repo2:1",
    name = "feature2",
    bundles = List(BundleConfig(url = "mvn:feature2:bundle1:1")),
    features = List(FeatureRef(url = "mvn:group1:repo3:1", names = List("feature3")))
  )

  val fullFeature3 = FeatureConfig(
    repoUrl = "mvn:group1:repo3:1",
    name = "feature3",
    features = List.empty,
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
