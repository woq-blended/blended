package blended.security.ssl.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.net.ssl.SSLContext
import org.osgi.framework.BundleActivator

class CertificateActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.security.ssl" -> new CertificateActivator()
  )

  "The Certificate Activator should" - {

    "start up and provide and SSL Context" in {
      assert(registry.getServiceReferences(classOf[SSLContext].getName(), "(type=server)").length == 1)
      assert(registry.getServiceReferences(classOf[SSLContext].getName(), "(type=client)").length == 1)
    }
  }

}
