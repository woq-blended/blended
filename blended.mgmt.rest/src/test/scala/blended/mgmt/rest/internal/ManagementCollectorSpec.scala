package blended.mgmt.rest.internal

import akka.testkit.TestLatch
import blended.spray.SprayUPickleSupport
import blended.updater.config._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import spray.testkit.ScalatestRouteTest

import scala.collection.immutable.Seq

import upickle.default._

class ManagementCollectorSpec
  extends WordSpec
    with Matchers
    with MockitoSugar
    with ScalatestRouteTest
    with CollectorService
    with SprayUPickleSupport {

  val testPostLatch = TestLatch(1)
  val testGetLatch = TestLatch(1)

  "The Management collector" should {

    "POST /container returns a registry response" in {
      Post("/container", ContainerInfo("uuid", Map("foo" -> "bar"), List(), List())) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
      testPostLatch.isOpen should be(true)
    }

    "GET /container return container infos" in {
      Get("/container") ~> infoRoute ~> check {
        responseAs[Seq[RemoteContainerState]] should be(Seq(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List(), List()), List())))
      }
      testGetLatch.isOpen should be(true)
    }

    "GET version returns the version" in {
      Get("/version") ~> versionRoute ~> check {
        responseAs[String] should be ("TEST")
      }
    }

  }

  override implicit def actorRefFactory = system

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    testPostLatch.countDown()
    ContainerRegistryResponseOK(info.containerId)
  }

  override def getCurrentState(): Seq[RemoteContainerState] = {
    testGetLatch.countDown()
    List(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List(), List()), List()))
  }

  override def version: String = "TEST"

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = ???

  override def getOverlayConfigs(): Seq[OverlayConfig] = ???

  override def getRuntimeConfigs(): Seq[RuntimeConfig] = ???

  override def registerOverlayConfig(oc: OverlayConfig): Unit = ???
}
