package blended.security.ssl.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import javax.net.ssl.SSLContext
import org.scalatest.FreeSpec

class CertificateActivatorSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  "The Certificate Activator should" - {

    "start up and provide and SSL Context" in {

      val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundle(sr)(
          "blended.security.ssl",
          Some(() => new CertificateActivator())
        ) { sr =>
          assert(sr.getServiceReferences(classOf[SSLContext].getName(), "(type=server)").length == 1)
          assert(sr.getServiceReferences(classOf[SSLContext].getName(), "(type=client)").length == 1)
          //TODO : Verify keystore
        }
      }
    }
  }
}
