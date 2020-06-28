package blended.updater.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.BlendedTestSupport
import java.io.File

class ResolvedProfileSpec extends LoggingFreeSpecLike with Matchers {
  
  private val featureDir : File = new File(BlendedTestSupport.projectTestOutput)

  "A Config with features references" - {

    val config : String = """
                   |name = name
                   |version = 1
                   |bundles = [{url = "mvn:base:bundle1:1"}]
                   |startLevel = 10
                   |defaultStartLevel = 10
                   |features = [
                   |  { url = "http://foobar.com/featuregrp/featurename/1", names = ["feature1"] }
                   |  { url = "http://foobar.com/featuregrp/featurename/1", names = ["feature2"] }
                   |]
                   |""".stripMargin

    val feature1 : String = """
                     |repoUrl = "http://foobar.com/featuregrp/featurename/1"
                     |name = feature1
                     |bundles = [{url = "mvn:feature1:bundle1:1"}]
                     |""".stripMargin

    val feature2 : String = """
                     |repoUrl = "http://foobar.com/featuregrp/featurename/1"
                     |name = feature2
                     |bundles = [{url = "mvn:feature2:bundle1:1"}]
                     |features = [{ url = "http://foobar.com/featuregrp/featurename/1", names = [ "feature3" ] }]
                     |""".stripMargin

    val feature3 : String = """
                     |repoUrl = "http://foobar.com/featuregrp/featurename/1"
                     |name = feature3
                     |bundles = [{url = "mvn:feature3:bundle1:1", startLevel = 0}]
                     |""".stripMargin

    val features : List[FeatureConfig] = List(feature1, feature2, feature3).map(f => {
      FeatureConfigCompanion.read(ConfigFactory.parseString(f)).get
    })

    val profile: Profile = ProfileCompanion.read(ConfigFactory.parseString(config)).get

    "should be constructable with extra features" in {
      ResolvedProfile(profile)
    }

    "should be constructable with optional resolved features" in {
      ResolvedProfile(profile.copy(resolvedFeatures = features))
    }

    "should not be constructable when some feature refs are not resolved" in {
      val ex = intercept[IllegalArgumentException] {
        ResolvedProfile(profile)
      }
      ex.getMessage should startWith("requirement failed: Contains resolved feature: feature1-1")
    }

    "should not be constructable when no bundle with startlevel 0 is present" in {
      // val f3 = features.find(_.name == "feature3").get
      //val fs = features.filter { _ != f3 } ++ Seq(f3.copy(bundles = f3.bundles.map(_.copy(startLevel = None))))
      val ex = intercept[IllegalArgumentException] {
        ResolvedProfile(profile)
      }
      ex.getMessage should startWith(
        "requirement failed: A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0'")
    }

    "should not be constructable when cycles between feature refs exist" in {
      pending
    }

    "should migrate all known features into RuntimeConfig.resolvedFeatures" in {
      profile.resolvedFeatures shouldBe empty
      features should have size (3)

      val resolver : FeatureResolver = new FeatureResolver(featureDir, features)

      val rrc1 : ResolvedProfile = resolver.resolve(profile).get
      rrc1.profile.resolvedFeatures should have size (3)

      val rrc2 = ResolvedProfile(rrc1.profile)
      rrc1.allReferencedFeatures.get should contain theSameElementsAs (rrc2.allReferencedFeatures.get)

      rrc1 should equal(rrc2)
    }

  }

}
