package blended.security.scep.standalone

import java.io.File
import java.util.ServiceLoader

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Promise
import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.security.ssl.{CertificateManager, MemoryKeystore}
import blended.util.logging.Logger
import domino.DominoActivator
import org.apache.felix.connect.launch.ClasspathScanner
import org.apache.felix.connect.launch.PojoServiceRegistryFactory

import scala.util.Success

class CertRefresher(salt: String) {

  private[this] val log = Logger[CertRefresher]

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  val baseDir = {
    val baseDir = new File(".").getAbsolutePath()
    System.setProperty("blended.container.home", baseDir)
    System.setProperty("scepclient.home", baseDir)
    baseDir
  }

  lazy val registry = {
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
    registry.getBundleContext().getBundle().stop(0)
  }

  def checkCert(): Future[MemoryKeystore] = {

    val promise = Promise[MemoryKeystore]()

    Future {
      new DominoActivator {
        whenBundleActive {
          whenServicePresent[CertificateManager] { certMgr =>
            log.debug(s"About to check and refresh certificates with cert manager [${certMgr}]")
            certMgr.checkCertificates().get match {
              case None =>
                log.error("No server certificates configured")
                throw new Exception("Server configuration is required to updated server certificates.")
              case Some(ms) =>
                log.debug("configured certificates checked successfully")
                promise.complete(Success(ms))
            }

            log.debug("Certificate checking finished, self-stopping bundle")
            bundleContext.getBundle().stop()
          }
        }
      }.start(registry.getBundleContext())
    }

    promise.future
  }

}
