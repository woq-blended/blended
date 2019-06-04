package blended.util.config

import com.typesafe.config.ConfigFactory
import org.scalatest.FreeSpec

class ConfigAccessorSpec extends FreeSpec with ConfigAccessor {

  "config as map" in {

    val configTxt = """|paths {
                  |  heise {
                  |    uri = "http://heise.de"
                  |    timeout = 30
                  |  }
                  |  google {
                  |    uri = "http://google.de"
                  |    timeout = 30
                  |  }
                  |}""".stripMargin

    val config = ConfigFactory.parseString(configTxt)

    val pathsConfigs = configConfigMap(config, "paths")

    assert(pathsConfigs.isDefined)
    assert(pathsConfigs.get.keySet === Set("heise", "google"))

    val heise = pathsConfigs.get("heise")

    assert(heise.hasPath("uri"))
    assert(heise.hasPath("timeout"))
  }

}
