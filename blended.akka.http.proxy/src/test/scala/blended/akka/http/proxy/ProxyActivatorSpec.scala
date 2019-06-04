package blended.akka.http.proxy

import java.io.File

import blended.akka.http.HttpContext
import blended.akka.internal.BlendedAkkaActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator

import scala.concurrent.duration._

class ProxyActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http.proxy" -> new BlendedAkkaHttpProxyActivator()
  )

  "The HTTP proxy activator" - {

    "should register the proxy routes as Simple Http context services" in {

      implicit val timeout : FiniteDuration = 3.seconds
      mandatoryService[HttpContext](registry)(None)
    }
  }
}
