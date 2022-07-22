package blended.updater.config

import blended.updater.config.json.PrickleProtocol._
import blended.util.logging.Logger
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import prickle._

import scala.reflect.{ClassTag, classTag}
import scala.util.Success
import scala.util.control.NonFatal

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

      val info =
        Pickle.intoString(List(ContainerInfo("c840c57d-a357-4b85-937a-2bb6440417d2", Map(), svcInfos, List(), 1L)))
      log.info("serialized: " + info)

      val containerInfos = Unpickle[List[ContainerInfo]].fromString(info).get
      containerInfos.size should be(1)
    }

  }

  "Prickle should (de)serialize" - {

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
      val profile = ProfileRef("myProfile", "1.0")

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile), 1L)

      val json = Pickle.intoString(info)
      log.info("json: " + json)

      val info2 = Unpickle[ContainerInfo].fromString(json).get

      info2.containerId should be(info2.containerId)
      info2.properties should be(Map("foo" -> "bar"))

      info2.serviceInfos should be(List(svcInfo))
      info2.profiles should be(List(profile))
    }

    "a ContainerRegistryResponseOK" in logException {
      val resp = ContainerRegistryResponseOK("response")

      val json = Pickle.intoString(resp)
      log.info("json: " + json)

      val resp2 = Unpickle[ContainerRegistryResponseOK].fromString(json).get
      resp2 should be(resp)

    }

    "a RemoteContainerState" in logException {
      val svcInfo = ServiceInfo("mySvc", "myType", System.currentTimeMillis(), 1000L, Map("svc" -> "test"))
      val profile = ProfileRef("myProfile", "1.0")

      val info = ContainerInfo("myId", Map("foo" -> "bar"), List(svcInfo), List(profile), 1L)

      val state = RemoteContainerState(info)

      val json = Pickle.intoString(state)
      log.info("json: " + json)

      val state2 = Unpickle[RemoteContainerState].fromString(json).get

      state2 should be(state)
    }

  }

  "Prickle maps and unmaps to identity" - {

    import TestData._

    def testMapping[T: ClassTag](g : Gen[T])(implicit
      u: Unpickler[T],
      p: Pickler[T]): Unit = {
      classTag[T].runtimeClass.getSimpleName in logException {
        forAll(g) { d: T =>
          val backAndForth = Unpickle[T].fromString(Pickle.intoString(d))
          // log.info(s"data: [${backAndForth}]")
          assert(backAndForth === Success(d))
        }
      }
    }

    testMapping[Artifact](artifacts)
//    testMapping[BundleConfig]
//    testMapping[FeatureRef]
    //testMapping[FeatureConfig]
//    testMapping[Profile]
//    testMapping[ServiceInfo]
//    testMapping[GeneratedConfig]
//    testMapping[ProfileRef]
//    testMapping[RolloutProfile]
//    testMapping[ContainerInfo]
//    testMapping[RemoteContainerState]
  }

  def logException[T](f: => T): T =
    try {
      f
    } catch {
      case NonFatal(e) =>
        log.error(e)("Exception caught")
        throw e
    }

}
