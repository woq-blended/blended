package blended.updater.config

import java.io.File

import scala.io.Source
import scala.util.Success
import blended.testsupport.{BlendedTestSupport, TestFile}
import blended.testsupport.TestFile.DeletePolicy
import blended.testsupport.scalatest.LoggingFreeSpecLike
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import scala.util.Try

class ProfileSpec
  extends LoggingFreeSpecLike
  with Matchers
  with TestFile {

  private val featureDir : File = new File(BlendedTestSupport.projectTestOutput)
  implicit val deletePolicy : DeletePolicy = TestFile.DeleteWhenNoFailure

  "Minimal config" - {

    val minimal = """
      |name = name
      |version = 1.0.0
      |bundles = [{ url = "http://example.org", jarName = "bundle1.jar", sha1Sum = sum, startLevel = 0 }]
      |startLevel = 10
      |defaultStartLevel = 10
      |""".stripMargin

    "read" in {
      ProfileCompanion.read(ConfigFactory.parseString(minimal)).get
    }

    val lines : Array[String] = minimal.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      s"without line [$n : ${lines(n)}] must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        val ex = intercept[Exception] {
          val profile : Profile = ProfileCompanion.read(ConfigFactory.parseString(config.mkString("\n"))).get
          ResolvedProfile(profile)
        }
        assert(ex.isInstanceOf[ConfigException] || ex.isInstanceOf[ResolvedProfileException])
      }
    }

    "read -> toConfig -> read must result in same config" in logException {
      import ProfileCompanion._
      val config = read(ConfigFactory.parseString(minimal))
      assert(config === read(toConfig(config.get)))
    }
  }

  "resolveFileName" - {
    "should infer the correct filename from a file URL" in logException {
      val bundle = BundleConfig(url = "file:///tmp/file1.jar", startLevel = 0)
      val rc = Profile(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url) === Success("file1.jar"))
    }
    "should infer the correct filename from a http URL" in {
      val bundle = BundleConfig(url = "http:///tmp/file1.jar", startLevel = 0)
      val rc = Profile(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url) === Success("file1.jar"))
    }
    "should infer the correct filename from a mvn URL without a repo setting" in {
      val bundle = BundleConfig(url = "mvn:group:file:1", startLevel = 0)
      val rc = Profile(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url) === Success("file-1.jar"))
    }

  }

  "A Config with features references" - {
    val config = """
      |name = name
      |version = 1
      |bundles = [{url = "mvn:base:bundle1:1"}]
      |startLevel = 10
      |defaultStartLevel = 10
      |features = [
      |  { url = "mvn:featuregrp:feature1:1", names = ["feature1", "feature2" ] }
      |]
      |""".stripMargin

    val feature1 = """
      |repoUrl = "mvn:featuregrp:feature1:1"
      |name = "feature1"
      |bundles = [{url = "mvn:feature1:bundle1:1"}]
      |""".stripMargin

    val feature2 = """
      |repoUrl = "mvn:featuregrp:feature1:1"
      |name = "feature2"
      |bundles = [{url = "mvn:feature2:bundle1:1"}]
      |features = [{url = "mvn:featuregrp:feature1:1", names = [ "feature3" ] }]
      |""".stripMargin

    val feature3 = """
      |repoUrl = "mvn:featuregrp:feature1:1"
      |name = "feature3"
      |bundles = [{url = "mvn:feature3:bundle1:1", startLevel = 0}]
      |""".stripMargin

    def features : List[FeatureConfig] = List(feature1, feature2, feature3).map(f => {
      val fc = FeatureConfigCompanion.read(ConfigFactory.parseString(f))
      fc shouldBe a[Success[_]]
      fc.get
    })

    "should resolve to a valid config" in logException {

      val resolver : FeatureResolver = new FeatureResolver(featureDir = featureDir, features = features)
      assert(resolver.resolve(FeatureRef("mvn:featuregrp:feature1:1", List("feature3"))).isSuccess)

      val rcTry = ProfileCompanion.read(ConfigFactory.parseString(config))
      rcTry shouldBe a[Success[_]]

      val resolvedTry : Try[ResolvedProfile] = resolver.resolve(rcTry.get)
      resolvedTry shouldBe a[Success[_]]
      val resolved : ResolvedProfile = resolvedTry.get

      resolved.profile.bundles should have size (1)
      resolved.allBundles.get should have size (4)
      resolved.profile.features should have size (1)
    }

  }

  "Download" - {
    1.to(10).foreach { i =>
      s"should download a local file (try ${i})" in {
        withTestFiles("content", "") { (file, target) =>
          assert(Source.fromFile(file).getLines().toList === List("content"), "Precondition failed")
          val result = ProfileCompanion.download(file.toURI().toString(), target)
          assert(result === Success(target))
          assert(Source.fromFile(target).getLines().toList === List("content"))
        }
      }
    }
  }

}
