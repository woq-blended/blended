package blended.mgmt.base

import org.scalatest.{Matchers, WordSpec}
import blended.updater.config._

import scala.collection.immutable

class ContainerInfoSpec extends WordSpec with Matchers {

  import spray.json._

  "ContainerInfo" should {

    val serviceInfo = ServiceInfo("service", 1234567890L, 30000L, Map("prop1" -> "val1"))
    val profiles = List()
    val containerInfo = ContainerInfo("uuid", Map("foo" -> "bar"), List(serviceInfo), profiles)
    val expectedJson = """{"containerId":"uuid","properties":{"foo":"bar"},"serviceInfos":[{"name":"service","timestampMsec":1234567890,"lifetimeMsec":30000,"props":{"prop1":"val1"}}],"profiles":[]}"""

    "serialize to Json correctly" in {
      import blended.mgmt.base.json._
      //      import blended.mgmt.base.json.JsonProtocol._
      val json = containerInfo.toJson
      json.compactPrint should be(expectedJson)
    }

    "serialize from Json correctly" in {
      import blended.mgmt.base.json._
      val json = expectedJson.parseJson
      val info = json.convertTo[ContainerInfo]

      info should be(containerInfo)
    }

    "create the Persistence Properties correctly" in {
      pending
      //
      //      val info = ContainerInfo("uuid", Map("fooo" -> "bar"), List())
      //
      //      val props = info.persistenceProperties
      //
      //      props._1 should be(info.getClass.getName.replaceAll("\\.", "_"))
      //      props._2.size should be(2)
      //      props._2(DataObject.PROP_UUID) should be(PersistenceProperty[String]("uuid"))
      //      props._2("fooo") should be(PersistenceProperty[String]("bar"))
    }

  }

  "ContainerRegistryResponseOK" should {

    val runtimeConfig = RuntimeConfig(name = "testprofile", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:g:a:v", startLevel = 0)))

    val addAction = AddRuntimeConfig(runtimeConfig)
    val stageAction = StageProfile(runtimeConfig.name, runtimeConfig.version, overlays = Set())
    val activateAction = ActivateProfile(profileName = "testprofile", profileVersion = "1", overlays = Set())

    val response = ContainerRegistryResponseOK("uuid", immutable.Seq(addAction, stageAction, activateAction))

    "serialize and deserialize result in equal object" in {
      import blended.mgmt.base.json._
      response should be(response.toJson.compactPrint.parseJson.convertTo[ContainerRegistryResponseOK])
    }

  }

  "OverlayRef" should {
    "serialize and deserialize" in {
      val overlayRef = OverlayRef("name", "version")
      import blended.mgmt.base.json._
      overlayRef should be(overlayRef.toJson.compactPrint.parseJson.convertTo[OverlayRef])
    }
  }

  "OverlaySet" should {
    "serialize and deserialize" in {
      val overlaySet = OverlaySet(overlays = List(OverlayRef("name", "version")), state = OverlayState.Active, reason = None)
      import blended.mgmt.base.json._
      overlaySet should be(overlaySet.toJson.compactPrint.parseJson.convertTo[OverlaySet])
    }
  }

}
