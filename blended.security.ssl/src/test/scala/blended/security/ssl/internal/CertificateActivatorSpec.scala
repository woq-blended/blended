package blended.security.ssl.internal

import java.io.File

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
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

  private val log = Logger[CertificateActivatorSpec]

  private implicit val timeout : FiniteDuration = 3.seconds

  "The Certificate Activator should" - {

    "start up and provide a server and a client SSL Context" in {

      mandatoryService[SSLContext](registry)(Some("(type=server)"))
      mandatoryService[SSLContext](registry)(Some("(type=client)"))

      val hasher = new PasswordHasher(pojoUuid)

      val jks = new JavaKeystore(
        store = new File(baseDir, "etc/keystore"),
        storepass = hasher.password("blended").toCharArray(),
        keypass = Some(hasher.password("mysecret").toCharArray())
      )

      val mks = jks.loadKeyStore().get
      mks.certificates.keys.toList.sorted should be (List("default", "logical"))
    }

    "Only support selected CypherSuites and protocols" in {

      val sslInfo : blended.security.ssl.SslContextInfo = mandatoryService[blended.security.ssl.SslContextInfo](registry)(None)
      mandatoryService[SSLContext](registry)(Some("(type=server)"))

      val invalid = sslInfo.getInvalidCypherSuites()
      log.info(s"Invalid CypherSuites [${invalid.size}] : [\n${invalid.mkString("\n")}\n]")

      invalid should be (empty)
    }
  }

}
