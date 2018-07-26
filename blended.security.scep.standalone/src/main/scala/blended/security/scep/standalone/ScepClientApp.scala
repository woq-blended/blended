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

object ScepClientApp {

  private[this] val log = org.log4s.getLogger

  def main(args: Array[String]): Unit = {
    //    Thread.currentThread().setPriority(Thread.MAX_PRIORITY)
    //    val scan = new ClasspathScanner().scanForBundles("(Bundle-SymbolicName=*)")

    val baseDir = new File(".").getAbsolutePath()
    System.setProperty("blended.container.home", baseDir)
    System.setProperty("scepclient.home", baseDir)

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
          override lazy val uuid: String = "simple"
        }
        idService.providesService[ContainerIdentifierService]
        log.debug(s"Provided idService: ${idService}")
      }
    }
    idServProvider.start(registry.getBundleContext())

    def stopApp(code: Int = 0): Unit = {
      // stop the framework
      registry.getBundleContext().getBundle.stop(0)
      // signal a proper exit code
      System.exit(code)
    }

    implicit val executionContext = scala.concurrent.ExecutionContext.global

    def uuid(): Future[String] = {
      val promise = Promise[String]()
      Future {
        new DominoActivator {
          whenBundleActive {
            whenServicePresent[ContainerIdentifierService] { idServ =>
              promise.success(idServ.uuid)
              bundleContext.getBundle.stop()
            }
          }
        }.start(registry.getBundleContext())
      }
      promise.future
    }

    def hashedPassword(pass: String, salt: String): String = new PasswordHasher(salt).password(pass)

    def hashedPasswordDefaultSalt(pass: String): Future[String] = uuid().map(salt => new PasswordHasher(salt).password(pass))

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

    def printAndStop[T](f: => Future[T]): Unit = {
      f.onComplete {
        case Success(t) =>
          println(t)
          stopApp()
        case Failure(e) =>
          println(e)
          stopApp(1)
      }
    }

    if (args.size == 2 && args(0) == "-p") {
      // -p pass
      printAndStop(hashedPasswordDefaultSalt(args(1)))
    } else if (args.size == 3 && args(0) == "-p") {
      // -p pass salt
      printAndStop(Future { hashedPassword(args(1), args(2)) })
    } else {
      // (default, regen or create initial keystore
      printAndStop(checkCert())
    }
  }

}