package blended.security.ssl.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.net.ssl.SSLContext
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

class CertificateActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.security.ssl" -> new CertificateActivator()
  )

  "The Certificate Activator should" - {

    "start up and provide a server and a client SSL Context" in {

      implicit val timeout : FiniteDuration = 3.seconds

      val serverContext : SSLContext = mandatoryService[SSLContext](registry)(Some("(type=server)"))
      val clientContext : SSLContext = mandatoryService[SSLContext](registry)(Some("(type=server)"))

      val hasher = new PasswordHasher(pojoUuid)

      val jks = new JavaKeystore(
        keystore = new File(baseDir, "etc/keystore"),
        storepass = hasher.password("blended").toCharArray(),
        keypass = Some(hasher.password("mysecret").toCharArray())
      )

      val mks = jks.loadKeyStore().get
      mks.certificates.keys.toList.sorted should be (List("default", "logical"))
    }
  }

}
