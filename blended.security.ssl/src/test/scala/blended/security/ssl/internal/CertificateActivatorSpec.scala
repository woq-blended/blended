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

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.security.ssl" -> new CertificateActivator()
  )

  private val log = Logger[CertificateActivatorSpec]

  private implicit val timeout : FiniteDuration = 3.seconds

  "The Certificate Activator should" - {

    "start up and provide a server and a client SSL Context" in {

      mandatoryService[SSLContext](registry)(Some("(type=server)"))
      mandatoryService[SSLContext](registry)(Some("(type=client)"))

      val jks : JavaKeystore = new JavaKeystore(
        store = new File(baseDir, "etc/keystore"),
        storepass = "e2e63a747c4c633e11d5f41f0297c020".toCharArray(),
        keypass = Some("e96504e3aeba28e8a3ed39116829e0da".toCharArray())
      )

      val mks = jks.loadKeyStore().get
      mks.certificates.keys.toList.sorted should be(List("default", "logical"))
    }

    "Only support selected CypherSuites and protocols" in {

      val sslInfo : blended.security.ssl.SslContextInfo = mandatoryService[blended.security.ssl.SslContextInfo](registry)(None)

      val invalid = sslInfo.getInvalidCypherSuites()
      log.info(s"Invalid CypherSuites [${invalid.length}] : [\n${invalid.mkString("\n")}\n]")

      invalid should be(empty)
    }
  }

}
