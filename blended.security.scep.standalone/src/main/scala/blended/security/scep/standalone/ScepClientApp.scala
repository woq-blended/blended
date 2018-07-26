package blended.security.scep.standalone

import org.apache.felix.connect.launch.ClasspathScanner
import org.apache.felix.connect.launch.PojoServiceRegistryFactory
import scala.collection.JavaConverters._
import java.util.ServiceLoader
import blended.security.ssl.CertificateManager
import domino.DominoActivator
import java.io.File
import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.security.ssl.internal.PasswordHasher
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import blended.security.ssl.internal.ServerKeyStore
import de.tototec.cmdoption.CmdlineParser

object ScepClientApp {

  private[this] val log = org.log4s.getLogger

  def main(args: Array[String]): Unit = {
    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    cp.setProgramName("java -jar scep-client.jar")
    cp.setAboutLine("Standalone SCEP client, which can create and update Java key stores from a remote SCEP server.")
    cp.parse(args: _*)

    if (cmdline.help || args.isEmpty) {
      cp.usage()
      return
    }

    val salt = cmdline.salt.getOrElse("scep-client")

    cmdline.password.foreach { pass =>
      println(new PasswordHasher(salt).password(pass))
    }

    if (cmdline.refreshCerts) {
      val refresher = new CertRefresher(salt)
      val result = refresher.checkCert()
      implicit val executionContext = scala.concurrent.ExecutionContext.global
      result.onComplete {
        case Success(r) =>
          println(s"Successfully refreshed certificates")
          refresher.stop()
          
          System.exit(0)  // ! Hard exit !
        
        case Failure(e) =>
          println(s"Error: Could not refresh certificates.\nReason: ${e.getMessage()}\nSee log file for details.")
          refresher.stop()
        
          System.exit(1) // ! Hard exit !
      
      }
    }

  }
}

class CertRefresher(salt: String) {

  private[this] val log = org.log4s.getLogger

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  val baseDir = {
    val baseDir = new File(".").getAbsolutePath()
    System.setProperty("blended.container.home", baseDir)
    System.setProperty("scepclient.home", baseDir)
    baseDir
  }

  val registry = {
    // Start Felix Connect Runtime

    val symbolicNames = List(
      "blended.security.ssl",
      "blended.security.scep"
    )
    val bundleFilter = s"(|${symbolicNames.map(n => s"(Bundle-SymbolicName=${n}*)").mkString("")})"
    val bundles = new ClasspathScanner().scanForBundles(bundleFilter)
    log.debug(s"Found bundles: ${bundles.asScala.map(b => s"${b.getHeaders().get("Bundle-SymbolicName")} -> ${b}").mkString("\n")}")

    val config = Map[String, Object](
      PojoServiceRegistryFactory.BUNDLE_DESCRIPTORS -> bundles
    )

    val loader = ServiceLoader.load(classOf[PojoServiceRegistryFactory])
    val factory = loader.iterator().next()
    log.debug(s"Found factory: ${factory}")

    val registry = factory.newPojoServiceRegistry(config.asJava)
    log.debug(s"Created registry: ${registry}")

    val idServProvider = new DominoActivator {
      whenBundleActive {
        val ctCtxt = new ScepAppContainerContext(baseDir)
        // This needs to be a fixed uuid as some tests might be for restarts and require the same id
        val idService = new ContainerIdentifierServiceImpl(ctCtxt) {
          override lazy val uuid: String = salt
        }
        idService.providesService[ContainerIdentifierService]
        log.debug(s"Provided idService: ${idService}")
      }
    }
    idServProvider.start(registry.getBundleContext())

    registry
  }

  def stop(): Unit = {
    registry.getBundleContext().getBundle.stop(0)
  }

  def checkCert(): Future[(ServerKeyStore, List[String])] = {
    val promise = Promise[(ServerKeyStore, List[String])]()
    Future {
      new DominoActivator {
        whenBundleActive {
          whenServicePresent[CertificateManager] { certMgr =>
            val checked = certMgr.checkCertificates()
            promise.complete(checked)
            bundleContext.getBundle.stop()
          }
        }
      }.start(registry.getBundleContext())
    }
    promise.future
  }

}
