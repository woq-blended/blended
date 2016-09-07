package blended.updater.config

import org.scalatest.FreeSpecLike
import org.scalatest.Matchers
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import scala.util.Success
import scala.util.Failure
import blended.testsupport.TestFile
import java.io.File
import scala.io.Source
import java.io.FileWriter
import scala.collection.immutable

class RuntimeConfigTest
    extends FreeSpecLike
    with Matchers
    with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "Minimal config" - {

    val minimal = """
      |name = name
      |version = 1.0.0
      |bundles = [{ url = "http://example.org", jarName = "bundle1.jar", sha1Sum = sum, startLevel = 0 }]
      |startLevel = 10
      |defaultStartLevel = 10
      |""".stripMargin

    "read" in {
      val config = RuntimeConfigCompanion.read(ConfigFactory.parseString(minimal)).get
    }

    val lines = minimal.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      "without line " + n + " must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        val ex = intercept[RuntimeException] {
          ResolvedRuntimeConfig(RuntimeConfigCompanion.read(ConfigFactory.parseString(config.mkString("\n"))).get, List())
        }
        assert(ex.isInstanceOf[ConfigException] || ex.isInstanceOf[IllegalArgumentException])
      }
    }

    "read -> toConfig -> read must result in same config" in {
      import RuntimeConfigCompanion._
      val config = read(ConfigFactory.parseString(minimal))
      assert(config === read(toConfig(config.get)))
    }
  }

  "resolveFileName" - {
    "should infer the correct filename from a file URL" in {
      val bundle = BundleConfig(url = "file:///tmp/file1.jar", start = false, startLevel = 0)
      val rc = RuntimeConfig(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url) === Success("file1.jar"))
    }
    "should infer the correct filename from a http URL" in {
      val bundle = BundleConfig(url = "http:///tmp/file1.jar", start = false, startLevel = 0)
      val rc = RuntimeConfig(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url) === Success("file1.jar"))
    }
    "should infer the correct filename from a mvn URL without a repo setting" in {
      val bundle = BundleConfig(url = "mvn:group:file:1", start = false, startLevel = 0)
      val rc = RuntimeConfig(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
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
      |  { name = feature1, version = 1 }
      |  { name = feature2, version = 1 }
      |]
      |""".stripMargin

    val feature1 = """
      |name = feature1
      |version = 1
      |bundles = [{url = "mvn:feature1:bundle1:1"}]
      |""".stripMargin

    val feature2 = """
      |name = feature2
      |version = 1
      |bundles = [{url = "mvn:feature2:bundle1:1"}]
      |features = [{name = feature3, version = 1}]
      |""".stripMargin

    val feature3 = """
      |name = feature3
      |version = 1
      |bundles = [{url = "mvn:feature3:bundle1:1", startLevel = 0}]
      |""".stripMargin

    def features = List(feature1, feature2, feature3).map(f => {
      val fc = FeatureConfigCompanion.read(ConfigFactory.parseString(f))
      fc shouldBe a[Success[_]]
      fc.get
    })

    //    "should read but not validate" in {
    //      val rcTry = RuntimeConfig.read(ConfigFactory.parseString(config))
    //      rcTry shouldBe a[Success[_]]
    //
    //      val rc = rcTry.get
    //      val rrc = ResolvedRuntimeConfig(rc, features.take(1))
    //      rc.bundles should have size (1)
    //      rc.features should have size (2)
    //      rrc.allBundles should have size (1)
    //
    //      val validate = rrc.validate()
    //      validate should have length (1)
    //      validate.head should include("one bundle with startLevel '0'")
    //    }

    val resolver = FeatureResolver

    "should resolve to a valid config" in {
      val rcTry = RuntimeConfigCompanion.read(ConfigFactory.parseString(config))
      rcTry shouldBe a[Success[_]]

      val resolvedTry = resolver.resolve(rcTry.get, features)
      resolvedTry shouldBe a[Success[_]]
      val resolved = resolvedTry.get
      resolved.runtimeConfig.bundles should have size (1)
      resolved.allBundles should have size (4)
      resolved.runtimeConfig.features should have size (2)
    }

  }

  "Property file generator" - {

    val bundle0 = BundleConfig(url = "http://b0.jar", startLevel = 0)
    val prev = RuntimeConfig(name = "test", version = "1", startLevel = 5, defaultStartLevel = 5, bundles = List(bundle0))
    val next = prev.copy(version = "2")

    "should not write a properties file without required settings" in {
      withTestDir() { dir =>
        val res = RuntimeConfigCompanion.createPropertyFile(
          LocalRuntimeConfig(ResolvedRuntimeConfig(next), new File(dir, "test/1")),
          Option(LocalRuntimeConfig(ResolvedRuntimeConfig(next), new File(dir, "test/2"))))
        assert(res === None)
      }
    }

    "should write properties file without a previous config" in {
      withTestDir() { dir =>
        sys.props += "TEST_A" -> "TEST_a"
        sys.props += "test.prop" -> "TEST_PROP"
        try {
          val res = RuntimeConfigCompanion.createPropertyFile(
            LocalRuntimeConfig(ResolvedRuntimeConfig(next.copy(properties = Map(
              RuntimeConfig.Properties.PROFILE_PROPERTY_FILE -> "etc/props",
              RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS -> "sysprop",
              RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS -> "TEST_A,test.prop"
            ))), new File(dir, "test/2")), None)
          val expectedTargetFile = new File(dir, "test/2/etc/props")
          assert(res === Some(Success(expectedTargetFile)))
          assert(Source.fromFile(expectedTargetFile).getLines.drop(2).toSet === Set("TEST_A=TEST_a", "test.prop=TEST_PROP"))
        } finally {
          sys.props -= "TEST_A"
          sys.props -= "test.prop"
        }
      }
    }

    "should write properties file from previous config" in {
      withTestDir() { dir =>
        val sourceFile = new File(dir, "test/1/etc/props")
        val expectedTargetFile = new File(dir, "test/2/etc/props") {
          sourceFile.getParentFile().mkdirs()
          val w = new FileWriter(sourceFile)
          w.append("test.prop=TEST_PROP")
          w.close()
        }

        val res = RuntimeConfigCompanion.createPropertyFile(
          LocalRuntimeConfig(ResolvedRuntimeConfig(next.copy(properties = Map(
            RuntimeConfig.Properties.PROFILE_PROPERTY_FILE -> "etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS -> "fileCurVer:etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS -> "test.prop"
          ))), new File(dir, "test/2")),
          Some(LocalRuntimeConfig(ResolvedRuntimeConfig(prev), new File(dir, "test/1"))
          )
        )
        assert(res === Some(Success(expectedTargetFile)))
        assert(Source.fromFile(expectedTargetFile).getLines.drop(2).toSet === Set("test.prop=TEST_PROP"))
      }
    }

    "should not loose old non-mandatory properties but overwrite existing mandatory ones" in {
      withTestDir() { dir =>
        val sourceFile = new File(dir, "test/1/etc/props")
        val expectedTargetFile = new File(dir, "test/2/etc/props")

        {
          sourceFile.getParentFile().mkdirs()
          val w = new FileWriter(sourceFile)
          w.append("test.prop=TEST_PROP\n")
          w.append("test.prop2=TEST_PROP3")
          w.close()
        }
        {
          expectedTargetFile.getParentFile().mkdirs()
          val w = new FileWriter(expectedTargetFile)
          w.append("test.prop=TEST_PROP1\n")
          w.append("test.prop2=TEST_PROP2")
          w.close()
        }

        val res = RuntimeConfigCompanion.createPropertyFile(
          LocalRuntimeConfig(ResolvedRuntimeConfig(next.copy(properties = Map(
            RuntimeConfig.Properties.PROFILE_PROPERTY_FILE -> "etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS -> "fileCurVer:etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS -> "test.prop"
          ))), new File(dir, "test/2")),
          Some(LocalRuntimeConfig(ResolvedRuntimeConfig(prev), new File(dir, "test/1"))
          )
        )
        assert(res === Some(Success(expectedTargetFile)))
        assert(Source.fromFile(expectedTargetFile).getLines.drop(2).toSet === Set("test.prop=TEST_PROP", "test.prop2=TEST_PROP2"))
      }
    }

    "should accept new mandatory properties and their initial values" in {

      withTestDir() { dir =>
        val sourceFile = new File(dir, "test/1/etc/props")
        val expectedTargetFile = new File(dir, "test/2/etc/props")

        {
          sourceFile.getParentFile().mkdirs()
          val w = new FileWriter(sourceFile)
          w.append("test.prop2=TEST_PROP3")
          w.close()
        }

        {
          expectedTargetFile.getParentFile().mkdirs()
          val w = new FileWriter(expectedTargetFile)
          w.append("test.prop=NEW_VALUE\n")
          w.append("test.prop2=TEST_PROP2")
          w.close()
        }

        val res = RuntimeConfigCompanion.createPropertyFile(
          LocalRuntimeConfig(ResolvedRuntimeConfig(next.copy(properties = Map(
            RuntimeConfig.Properties.PROFILE_PROPERTY_FILE -> "etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS -> "fileCurVer:etc/props",
            RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS -> "test.prop"
          ))), new File(dir, "test/2")),
          Some(LocalRuntimeConfig(ResolvedRuntimeConfig(prev), new File(dir, "test/1"))
          )
        )
        assert(res === Some(Success(expectedTargetFile)))
        assert(Source.fromFile(expectedTargetFile).getLines.drop(2).toSet === Set("test.prop=NEW_VALUE", "test.prop2=TEST_PROP2"))
      }
    }
  }

  "Download" - {
    1.to(10).foreach { i =>
      s"should download a local file (try ${i})" in {
        withTestFiles("content", "") { (file, target) =>
          assert(Source.fromFile(file).getLines().toList === List("content"), "Precondition failed")
          val result = RuntimeConfigCompanion.download(file.toURI().toString(), target)
          assert(result === Success(target))
          assert(Source.fromFile(target).getLines().toList === List("content"))
        }
      }
    }
  }

}