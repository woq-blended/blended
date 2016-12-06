package blended.updater.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory
import upickle.default._

class UPickleSpec extends FreeSpec with Matchers {

  private[this] val log = LoggerFactory.getLogger(classOf[UPickleSpec])

  "A ContainerRegistryResponseOK should be (de)serializable with with uPickle" in {
    pending
    val resp = ContainerRegistryResponseOK("response", List.empty)

    val json = upickle.default.write(resp)
  }

  "An UpdateAction should be (de)serializable with uPickle" in {

    val overlay = OverlayRef("myOverlay", "1.0")
    val action = ActivateProfile(profileName = "test", profileVersion = "1.0", overlays = List(overlay))

    val json = upickle.default.write(action)

    val action2 : UpdateAction = upickle.default.read[UpdateAction](json)
    action2.isInstanceOf[ActivateProfile] should be (true)
    action2.isInstanceOf[UpdateAction] should be (true)

    val activate = action2.asInstanceOf[ActivateProfile]
    activate.profileName should be (action.profileName)
    activate.profileVersion should be (action.profileVersion)

    activate.overlays should be (List(overlay))
  }

  "An Overlay Config should be (de)serializable with uPickle" in {

    val cfg = OverlayConfig("test", "1.0", List.empty, Map.empty)

    val json = upickle.default.write(cfg)

    val cfg2 = upickle.default.read[OverlayConfig](json)

    cfg2.name should be (cfg.name)
  }

  "A Generated Config should be (de)serializable with uPickle" in {

    val config = ConfigFactory.load()

    val cfg = GeneratedConfigCompanion.create("filename", config)

    val json = upickle.default.write(cfg)

    val cfg2 = upickle.default.read[GeneratedConfig](json)
    cfg2.configFile should be (cfg.configFile)

    val config2 = GeneratedConfigCompanion.config(cfg2)
    config2 should be (config)

  }

  "A Container Info should be (de)serializable with uPickle" in {

    pending
//    import ContainerInfo._
//
//    val svcInfo = ServiceInfo("mySvc", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))
//    val profile = Profile("myProfile", "1.0", List.empty)
//
//    val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile))
//
//    val json = write(info)
//    log.info(json)
//
//    val info2 = read[ContainerInfo](json)
//
//    info2.containerId should be (info2.containerId)
//    info2.properties should be (Map("foo" -> "bar"))
//
//    info2.serviceInfos should be (List(svcInfo))
//    info2.profiles should be (List(profile))
  }

  "A List of Service Infos should be (de)serializable with uPickle" in {

    val svcInfo = ServiceInfo("mySvc", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))

    val json = write(List(svcInfo))

    val svcList = read[List[ServiceInfo]](json)

    svcList should be (List(svcInfo))
  }
}
