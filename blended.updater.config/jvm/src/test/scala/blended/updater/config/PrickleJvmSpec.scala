package blended.updater.config

import blended.updater.config.json.PrickleProtocol._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}
import prickle._

class PrickleJvmSpec extends FreeSpec with Matchers {

  "JVM: Prickle should (de)serialize" - {

    "a GeneratedConfig" in {

      val config = ConfigFactory.load()

      val cfg = GeneratedConfigCompanion.create("filename", config)

      val json = Pickle.intoString(cfg)

      val cfg2 = Unpickle[GeneratedConfig].fromString(json).get
      cfg2.configFile should be(cfg.configFile)

      val config2 = GeneratedConfigCompanion.config(cfg2)
      config2 should be(config)

    }

  }

}
