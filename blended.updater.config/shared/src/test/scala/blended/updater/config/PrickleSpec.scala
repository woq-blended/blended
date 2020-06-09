package blended.updater.config

import java.util.UUID

import blended.util.logging.Logger
import blended.updater.config.json.PrickleProtocol._
import org.scalacheck.Arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import prickle._
import scala.reflect.{ClassTag, classTag}
import scala.util.Success
import scala.util.control.NonFatal

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PrickleSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private[this] val log = Logger[PrickleSpec]

  "Prickle real world test cases" - {
    "1. deserialize a container info" in logException {

      val svcInfos = List(
        ServiceInfo(
          "org.apache.activemq:type=Broker,brokerName=blended,destinationType=Queue,destinationName=SampleIn",
          "jmsQueue",
          System.currentTimeMillis(),
          3000L,
          Map(
            "Name" -> "SampleIn",
            "InFlightCount" -> "0",
            "DequeueCount" -> "0",
            "QueueSize" -> "0",
            "EnqueueCount" -> "0"
          )
        ),
        ServiceInfo(
          "org.apache.activemq:type=Broker,brokerName=blended,destinationType=Queue,destinationName=SampleIn,endpoint=Consumer,clientId=ID_x3.local-45075-1488278206331-3_1,consumerId=ID_x3.local-45075-1488278206331-4_1_2_1",
          "jmsQueue",
          System.currentTimeMillis(),
          3000L,
          Map(
            "Name" -> "SampleIn",
            "InFlightCount" -> "0",
            "DequeueCount" -> "0",
            "QueueSize" -> "0",
            "EnqueueCount" -> "0"
          )
        ),
        ServiceInfo(
          "java.lang:type=Memory",
          "Runtime",
          System.currentTimeMillis(),
          3000L,
          Map(
            "HeapMemoryUsage.committed" -> "383254528",
            "HeapMemoryUsage.init" -> "260046848",
            "HeapMemoryUsage.max" -> "3693084672",
            "HeapMemoryUsage.used" -> "135072144"
          )
        ),
        ServiceInfo(
          "java.lang:type=OperationSystem",
          "Runtime",
          System.currentTimeMillis(),
          3000L,
          Map(
            "Name" -> "Linux"
          )
        ),
        ServiceInfo(
          "akka://BlendedActorSystem/user/blended.updater",
          "Updater",
          System.currentTimeMillis(),
          3000L,
          Map(
            "profile.active" -> "blended.demo.node-2.1.0-SNAPSHOT",
            "profiles.valid" -> "blended.demo.node-2.1.0-SNAPSHOT"
          )
        )
      )

      val info = Pickle.intoString(List(ContainerInfo("c840c57d-a357-4b85-937a-2bb6440417d2", Map(), svcInfos, List(), 1L, Nil)))
      log.info("serialized: " + info)

      val containerInfos = Unpickle[List[ContainerInfo]].fromString(info).get
      containerInfos.size should be(1)
    }

  }

  "Prickle should (de)serialize" - {
    "an ActivateProfile" in logException {

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(UUID.randomUUID().toString(), profileName = "test", profileVersion = "1.0", overlays = Set(overlay))

      val json = Pickle.intoString(action)

      val action2 : ActivateProfile = Unpickle[ActivateProfile].fromString(json).get
      action2.isInstanceOf[ActivateProfile] should be(true)
      action2.isInstanceOf[UpdateAction] should be(true)

      val activate = action2.asInstanceOf[ActivateProfile]
      activate.profileName should be(action.profileName)
      activate.profileVersion should be(action.profileVersion)

      activate.overlays should be(Set(overlay))

    }

    "an ActivateProfile as UpdateAction" in logException {

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(UUID.randomUUID().toString(), profileName = "test", profileVersion = "1.0", overlays = Set(overlay))

      val json = Pickle.intoString(action : UpdateAction)
      log.info("json: " + json)

      val action2 : UpdateAction = Unpickle[UpdateAction].fromString(json).get
      action2.isInstanceOf[ActivateProfile] should be(true)
      action2.isInstanceOf[UpdateAction] should be(true)

      val activate = action2.asInstanceOf[ActivateProfile]
      activate.profileName should be(action.profileName)
      activate.profileVersion should be(action.profileVersion)

      activate.overlays should be(Set(overlay))
    }

    "a GeneratedConfig" in logException {

      val cfg = GeneratedConfig("filename", "{ key1: value1 }")
      val json = Pickle.intoString(cfg)
      val cfg2 = Unpickle[GeneratedConfig].fromString(json).get
      cfg2 should be(cfg)

    }

    "a ServiceInfo" in logException {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000L, Map("svc" -> "test"))

      val json = Pickle.intoString(svcInfo)
      log.info("json: " + json)

      val svc = Unpickle[ServiceInfo].fromString(json).get

      svc should be(svcInfo)
    }

    "a list of ServiceInfo's" in logException {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000L, Map("svc" -> "test"))

      val json = Pickle.intoString(List(svcInfo))
      log.info("json: " + json)

      val svcList = Unpickle[List[ServiceInfo]].fromString(json).get

      svcList should be(List(svcInfo))
    }

    "a ContainerInfo" in logException {

      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000L, Map("svc" -> "test"))
      val profile = Profile("myProfile", "1.0", OverlaySet(Set(), OverlayState.Valid, None))

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile), 1L, Nil)

      val json = Pickle.intoString(info)
      log.info("json: " + json)

      val info2 = Unpickle[ContainerInfo].fromString(json).get

      info2.containerId should be(info2.containerId)
      info2.properties should be(Map("foo" -> "bar"))

      info2.serviceInfos should be(List(svcInfo))
      info2.profiles should be(List(profile))
    }

    "a ContainerRegistryResponseOK" in logException {
      val resp = ContainerRegistryResponseOK("response", List.empty)

      val json = Pickle.intoString(resp)
      log.info("json: " + json)

      val resp2 = Unpickle[ContainerRegistryResponseOK].fromString(json).get
      resp2 should be(resp)

    }

    "a RemoteContainerState" in logException {
      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000L, Map("svc" -> "test"))
      val profile = Profile("myProfile", "1.0", OverlaySet(Set(), OverlayState.Valid, None))

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile), 1L, Nil)

      val overlay = OverlayRef("myOverlay", "1.0")
      val action = ActivateProfile(UUID.randomUUID().toString(), profileName = "test", profileVersion = "1.0", overlays = Set(overlay))

      val state = RemoteContainerState(info, List(action))

      val json = Pickle.intoString(state)
      log.info("json: " + json)

      val state2 = Unpickle[RemoteContainerState].fromString(json).get

      state2 should be(state)
    }

  }

  "Prickle maps and unmaps to identity" - {

    import TestData._

    def testMapping[T : ClassTag](implicit
      arb : Arbitrary[T],
      u : Unpickler[T],
      p : Pickler[T]
    ) : Unit = {
      classTag[T].runtimeClass.getSimpleName in logException {
        forAll { d : T =>
          val backAndForth = Unpickle[T].fromString(Pickle.intoString(d))
          // log.info(s"data: [${backAndForth}]")
          assert(backAndForth === Success(d))
        }
      }
    }

    testMapping[Artifact]
    testMapping[BundleConfig]
    testMapping[FeatureRef]
    testMapping[FeatureConfig]
    testMapping[OverlayConfig]
    testMapping[RuntimeConfig]
    testMapping[ServiceInfo]
    testMapping[UpdateAction]
    testMapping[GeneratedConfig]
    testMapping[ProfileGroup]
    testMapping[Profile]
    testMapping[OverlayRef]
    testMapping[OverlaySet]
    testMapping[RolloutProfile]
    testMapping[ContainerInfo]
    testMapping[RemoteContainerState]
  }

  def logException[T](f : => T) : T = try {
    f
  } catch {
    case NonFatal(e) =>
      log.error(e)("Exception caught")
      throw e
  }

}
