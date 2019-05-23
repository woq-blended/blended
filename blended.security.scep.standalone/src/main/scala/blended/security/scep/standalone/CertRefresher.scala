package blended.security.scep.standalone

import java.io.File
import java.util.ServiceLoader

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import blended.container.context.api.ContainerIdentifierService
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.security.ssl.{CertificateManager, MemoryKeystore}
import blended.util.logging.Logger
import domino.DominoActivator
import org.apache.felix.connect.launch.{ClasspathScanner, PojoServiceRegistryFactory}

/**
  *
  * @param salt
  * @param baseDir0 The base dir, used for the internal blended container configuration (etc, log)
  */
class CertRefresher(salt: String, baseDir0: File = new File(".")) {

  private[this] val log = Logger[CertRefresher]

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  val baseDir = {
    val baseDir = baseDir0.getAbsolutePath()
    System.setProperty("blended.container.home", baseDir)
    System.setProperty("scepclient.home", baseDir)
    baseDir
  }

  lazy val registry = {
    // Start Felix Connect Runtime
    log.debug(s"Starting felix connect runtime")

    val symbolicNames = List(
      "blended.security.ssl",
      "blended.security.scep"
    )
    val bundleFilter = s"(|${symbolicNames.map(n => s"(Bundle-SymbolicName=${n}*)").mkString("")})"
    val bundles = new ClasspathScanner().scanForBundles(bundleFilter, getClass().getClassLoader())
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
        log.debug(s"Starting ScepAppContainerContext with baseDir=${baseDir}")
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
            val result = certMgr.checkCertificates() match {
              case Failure(e) =>
                Failure(e)
              case Success(None) =>
                log.error("No server certificates configured")
                Failure(new Exception("Server configuration is required to updated server certificates."))
              case Success(Some(ms)) =>
                log.debug("configured certificates checked successfully")
                Success(ms)
            }
            promise.complete(result)

            log.debug("Certificate checking finished, self-stopping bundle")
            bundleContext.getBundle().stop()
          }
        }
      }.start(registry.getBundleContext())
    }

    promise.future
  }

}
