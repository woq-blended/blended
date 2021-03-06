package blended.updater.config

import blended.testsupport.BlendedTestSupport

import scala.util.Success
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class FeatureResolverSpec extends AnyFreeSpec with Matchers {

  private val featureDir : File = new File(BlendedTestSupport.projectTestOutput)
  private val toRef : FeatureConfig => FeatureRef = fc => FeatureRef(fc.repoUrl, List(fc.name))

  val fullFeature1 = FeatureConfig(
    repoUrl = "mvn:group1:repo1:1",
    name = "feature1",
    features = List.empty,
    bundles = List(BundleConfig(url = "mvn:feature1:bundle1:1", startLevel = 0))
  )
  val featureRef1 : FeatureRef = toRef(fullFeature1)

  val fullFeature2 = FeatureConfig(
    repoUrl = "mvn:group1:repo2:1",
    name = "feature2",
    bundles = List(BundleConfig(url = "mvn:feature2:bundle1:1")),
    features = List(FeatureRef(url = "mvn:group1:repo3:1", names = List("feature3")))
  )
  val featureRef2 : FeatureRef = toRef(fullFeature2)

  val fullFeature3 = FeatureConfig(
    repoUrl = "mvn:group1:repo3:1",
    name = "feature3",
    features = List.empty,
    bundles = List(BundleConfig(url = "mvn:feature3:bundle1:1"))
  )

  "An unresolved Feature" - {
    "should resolve" in {
      val resolver = new FeatureResolver(featureDir = featureDir, features = List(fullFeature1, fullFeature2, fullFeature3))

      val resolvedTry = resolver.resolve(feature = featureRef1)
      resolvedTry should equal(Success(List(fullFeature1)))
    }

    "should resolve transitive" in {
      val resolver = new FeatureResolver(featureDir = featureDir, features = List(fullFeature2, fullFeature3))
      val resolvedTry = resolver.resolve(featureRef2)
      resolvedTry shouldBe a[Success[_]]
      val result = resolvedTry.get
      result should have size 2

      result should contain (fullFeature2)
      result should contain (fullFeature3)
    }
  }

}
