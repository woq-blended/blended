package blended.mgmt.rest.internal

import akka.testkit.TestLatch
import blended.spray.SprayPrickleSupport
import blended.updater.config._
import org.scalatest.{ Matchers, FreeSpec }
import spray.testkit.ScalatestRouteTest
import blended.updater.config.json.PrickleProtocol._

import scala.collection.immutable.Seq

class ManagementCollectorSpec
    extends FreeSpec
    with Matchers
    with ScalatestRouteTest
    with CollectorService
    with SprayPrickleSupport {

  val testPostLatch = TestLatch(1)
  val testGetLatch = TestLatch(1)

  "The Management collector routes" - {

    "should POST /container returns a registry response" in {
      Post("/container", ContainerInfo("uuid", Map("foo" -> "bar"), List(), List())) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
      testPostLatch.isOpen should be(true)
    }

    "should GET /container return container infos" in {
      Get("/container") ~> infoRoute ~> check {
        responseAs[Seq[RemoteContainerState]] should be(Seq(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List(), List()), List())))
      }
      testGetLatch.isOpen should be(true)
    }

    "should GET version returns the version" in {
      Get("/version") ~> versionRoute ~> check {
        responseAs[String] should be("TEST")
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

  override def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit = ???
}
