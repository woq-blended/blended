package blended.akka.http.internal

import java.io.File
import java.net.URI

import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.internal.BlendedJmxActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{AkkaHttpServerTestHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.model.{StatusCode, Uri}

class HttpActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with AkkaHttpServerTestHelper {

  private implicit val backend = HttpURLConnectionBackend()
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator()
  )

  "The Akka Http Activator should" - {

    "start a HTTP server based on Akka HTTP" in {
      val request = basicRequest.get(Uri(new URI(s"${plainServerUrl(registry)}/about")))
      val response = request.send()

      assert(response.code == StatusCode.Ok)
    }
  }
}
