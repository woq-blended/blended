package blended.mgmt.rest.internal

import akka.testkit.TestLatch

import blended.akka.OSGIActorConfig
import blended.spray.SprayPrickleSupport
import blended.updater.config._
import org.scalatest.{FreeSpec, Matchers}
import spray.testkit.ScalatestRouteTest
import blended.updater.config.json.PrickleProtocol._
import blended.security.spray.DummyBlendedSecuredRoute

import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.duration._

class ManagementCollectorSpec
    extends FreeSpec
    with Matchers
    with ScalatestRouteTest
    with CollectorService
    with SprayPrickleSupport 
    with DummyBlendedSecuredRoute {

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

  override def cleanUp(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  override def actorConfig: OSGIActorConfig = ???

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
