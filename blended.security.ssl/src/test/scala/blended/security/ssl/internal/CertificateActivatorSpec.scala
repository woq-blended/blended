package blended.security.ssl.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.FreeSpec

class CertificateActivatorSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  "The Certificate Activator should" - {

    "start up and provide and SSL Context" in {

      val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundle[Unit](sr)(
          "blended.security.ssl",
          Some(() => new CertificateActivator())
        ) { sr =>
          // do some testing here
        }
      }
    }
  }

}
