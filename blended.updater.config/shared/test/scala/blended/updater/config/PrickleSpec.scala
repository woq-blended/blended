package blended.updater.config

import blended.updater.config.json.PrickleProtocol._
import org.scalatest.{ FreeSpec, Matchers }
import prickle._

class PrickleSpec extends FreeSpec with Matchers {

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
      println("json: " + json)

      val action2: UpdateAction = Unpickle[UpdateAction].fromString(json).get
      action2.isInstanceOf[ActivateProfile] should be(true)
      action2.isInstanceOf[UpdateAction] should be(true)

      val activate = action2.asInstanceOf[ActivateProfile]
      activate.profileName should be(action.profileName)
      activate.profileVersion should be(action.profileVersion)

      activate.overlays should be(List(overlay))
    }

    "a GeneratedConfig" in {

      val cfg = GeneratedConfig("filename", "{ key1: value1 }")
      val json = Pickle.intoString(cfg)
      val cfg2 = Unpickle[GeneratedConfig].fromString(json).get
      cfg2 should be(cfg)

    }

    "a ServiceInfo" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))

      val json = Pickle.intoString(svcInfo)
      println("json: " + json)

      val svc = Unpickle[ServiceInfo].fromString(json).get

      svc should be(svcInfo)
    }

    "a list of ServiceInfo's" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))

      val json = Pickle.intoString(List(svcInfo))
      println("json: " + json)

      val svcList = Unpickle[List[ServiceInfo]].fromString(json).get

      svcList should be(List(svcInfo))
    }

    "a ContainerInfo" in {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))
      val profile = Profile("myProfile", "1.0", List.empty)

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile))

      val json = Pickle.intoString(info)
      println("json: " + json)

      val info2 = Unpickle[ContainerInfo].fromString(json).get

      info2.containerId should be(info2.containerId)
      info2.properties should be(Map("foo" -> "bar"))

      info2.serviceInfos should be(List(svcInfo))
      info2.profiles should be(List(profile))
    }

    "a ContainerRegistryResponseOK" in {
      val resp = ContainerRegistryResponseOK("response", List.empty)

      val json = Pickle.intoString(resp)
      println("json: " + json)

      val resp2 = Unpickle[ContainerRegistryResponseOK].fromString(json).get
      resp2 should be(resp)

    }

    "a RemoteContainerState" in {
      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000l, Map("svc" -> "test"))
      val profile = Profile("myProfile", "1.0", List.empty)

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile))

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(profileName = "test", profileVersion = "1.0", overlays = List(overlay))

      val state = RemoteContainerState(info, List(action))

      val json = Pickle.intoString(state)
      println("json: " + json)

      val state2 = Unpickle[RemoteContainerState].fromString(json).get

      state2 should be (state)
    }

  }

}