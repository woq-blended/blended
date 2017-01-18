package blended.updater.config

import blended.updater.config.json.PrickleProtocol._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory
import prickle._

class PrickleSpec extends FreeSpec with Matchers {

  private[this] val log = LoggerFactory.getLogger(classOf[PrickleSpec])

  "Prickle should (de)serialize" - {
    "an ActivateProfile" in {

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(profileName = "test", profileVersion = "1.0", overlays = List(overlay))

      val json = Pickle.intoString(action)

      val action2: ActivateProfile = Unpickle[ActivateProfile].fromString(json).get
      action2.isInstanceOf[ActivateProfile] should be(true)
      action2.isInstanceOf[UpdateAction] should be(true)

      val activate = action2.asInstanceOf[ActivateProfile]
      activate.profileName should be(action.profileName)
      activate.profileVersion should be(action.profileVersion)

      activate.overlays should be(List(overlay))

    }

    "an ActivateProfile as UpdateAction" in {

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(profileName = "test", profileVersion = "1.0", overlays = List(overlay))

      val json = Pickle.intoString(action: UpdateAction)
      log.info("json: " + json)

      val action2: UpdateAction = Unpickle[UpdateAction].fromString(json).get
      action2.isInstanceOf[ActivateProfile] should be(true)
      action2.isInstanceOf[UpdateAction] should be(true)

      val activate = action2.asInstanceOf[ActivateProfile]
      activate.profileName should be(action.profileName)
      activate.profileVersion should be(action.profileVersion)

      activate.overlays should be(List(overlay))
    }

    "a GeneratedConfig" in {

      val config = ConfigFactory.load()

      val cfg = GeneratedConfigCompanion.create("filename", config)

      val json = Pickle.intoString(cfg)

      val cfg2 = Unpickle[GeneratedConfig].fromString(json).get
      cfg2.configFile should be(cfg.configFile)

      val config2 = GeneratedConfigCompanion.config(cfg2)
      config2 should be(config)

    }

    "a ServiceInfo" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))

      val json = Pickle.intoString(svcInfo)
      log.info("json: " + json)

      val svc = Unpickle[ServiceInfo].fromString(json).get

      svc should be(svcInfo)
    }

    "a list of ServiceInfo's" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))

      val json = Pickle.intoString(List(svcInfo))
      log.info("json: " + json)

      val svcList = Unpickle[List[ServiceInfo]].fromString(json).get

      svcList should be(List(svcInfo))
    }

    "a ContainerInfo" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))
      val profile = Profile("myProfile", "1.0", List.empty)

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile))

      val json = Pickle.intoString(info)
      log.info("json: " + json)

      val info2 = Unpickle[ContainerInfo].fromString(json).get

      info2.containerId should be(info2.containerId)
      info2.properties should be(Map("foo" -> "bar"))

      info2.serviceInfos should be(List(svcInfo))
      info2.profiles should be(List(profile))
    }

    "a ContainerRegistryResponseOK" in {
      val resp = ContainerRegistryResponseOK("response", List.empty)

      val json = Pickle.intoString(resp)
      log.info("json: " + json)

      val resp2 = Unpickle[ContainerRegistryResponseOK].fromString(json).get
      resp2 should be(resp)

    }

  }

}