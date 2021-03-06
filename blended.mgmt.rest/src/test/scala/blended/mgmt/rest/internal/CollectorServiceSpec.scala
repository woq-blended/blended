package blended.mgmt.rest.internal

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestLatch
import blended.prickle.akka.http.PrickleSupport
import blended.security.akka.http.DummyBlendedSecurityDirectives
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import scala.collection.{immutable => sci}
import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CollectorServiceSpec
    extends AnyFreeSpec
    with Matchers
    with ScalatestRouteTest
    with CollectorService
    with DummyBlendedSecurityDirectives
    with PrickleSupport {

  val processContainerInfoLatch = TestLatch(1)
  val getCurrentStateLatch = TestLatch(1)

  "The Management collector routes" - {

    "should POST /container returns a registry response" in {
      Post("/container", ContainerInfo("uuid", Map("foo" -> "bar"), List(), List(), 1L)) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
      processContainerInfoLatch.isOpen should be(true)
    }

    "should GET /container return container infos" in {
      Get("/container") ~> infoRoute ~> check {
        responseAs[Seq[RemoteContainerState]] should be(
          Seq(
            RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List(), List(), 1L))
          ))
      }
      getCurrentStateLatch.isOpen should be(true)
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

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    processContainerInfoLatch.countDown()
    ContainerRegistryResponseOK(info.containerId)
  }

  override def getCurrentState(): sci.Seq[RemoteContainerState] = {
    getCurrentStateLatch.countDown()
    List(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List(), List(), 1L)))
  }

  override def version: String = "TEST"

}
