package blended.updater.config

import org.scalatest.FreeSpecLike
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import scala.util.Success
import scala.util.Failure

class RuntimeConfigTest extends FreeSpecLike {

  "Minimal config" - {

    val minimal = """
      |name = name
      |version = 1.0.0
      |bundles = [{ url = "http://example.org", jarName = "bundle1.jar", sha1Sum = sum, startLevel = 0 }]
      |startLevel = 10
      |defaultStartLevel = 10
      |""".stripMargin

    "read" in {
      val config = RuntimeConfig.read(ConfigFactory.parseString(minimal)).get
    }

    val lines = minimal.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      "without line " + n + " must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        val ex = intercept[RuntimeException] {
          RuntimeConfig.read(ConfigFactory.parseString(config.mkString("\n"))).get
        }
        assert(ex.isInstanceOf[ConfigException.ValidationFailed] || ex.isInstanceOf[IllegalArgumentException])
      }
    }

    "read -> toConfig -> read must result in same config" in {
      import RuntimeConfig._
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
    "should not infer the correct filename from a mvn URL without a repo setting" in {
      val bundle = BundleConfig(url = "mvn:group:file:1", start = false, startLevel = 0)
      val rc = RuntimeConfig(name = "test", version = "1", bundles = List(bundle), startLevel = 1, defaultStartLevel = 1)
      assert(rc.resolveFileName(bundle.url).isInstanceOf[Failure[_]])
    }

  }

}