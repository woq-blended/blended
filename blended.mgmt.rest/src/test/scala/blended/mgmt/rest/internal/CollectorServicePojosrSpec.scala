package blended.mgmt.rest.internal

import java.io.File

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import akka.actor.ActorSystem
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.internal.BlendedJmxActivator
import blended.mgmt.repo.WritableArtifactRepo
import blended.mgmt.repo.internal.ArtifactRepoActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{AkkaHttpServerTestHelper, BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.retry.ResultPoller
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM, TestFile}
import blended.util.logging.Logger
import domino.DominoActivator
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import sttp.client._

@RequiresForkedJVM
class CollectorServicePojosrSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with TestFile
  with AkkaHttpServerTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.mgmt.repo" -> new ArtifactRepoActivator(),
    "blended.mgmt.rest" -> new MgmtRestActivator(),
    "blended.persistence.h2" -> new H2Activator()
  )

  private[this] val log = Logger[this.type]
  implicit val sttpBackend = HttpURLConnectionBackend()

  case class Server(serviceRegistry : BlendedPojoRegistry, dir : File)

  def withServer(hint : String)(f : Server => Unit) : Unit = {
    log.info(s"Server path: ${baseDir}")

    // cleanup potential left over data from previous runs
    deleteRecursive(
      new File(baseDir, "data"),
      new File(baseDir, "repositories")
    )
    // We consume services with a nice domino API
    new DominoActivator() {
      whenBundleActive {
        whenServicePresent[ActorSystem] { system =>
          whenServicePresent[WritableArtifactRepo] { _ =>
            whenAdvancedServicePresent[HttpContext]("(prefix=mgmt)") { _ =>
              implicit val eCtxt : ExecutionContext = system.dispatcher
              log.info("Test-Server up and running. Starting test case...")
              val success: Try[Unit] =
                new ResultPoller[Unit](system, timeout, hint)(() => Future {
                  f(Server(serviceRegistry = registry, dir = new File(baseDir)))
                }).execute(_ => ())
              assert(success.isSuccess)
            }
          }
        }
      }
    }.start(registry.getBundleContext())
  }

  "REST-API with a self-hosted HTTP server" - {

    "GET /version should return the version" in {
      withServer("Get Version") { sr =>
        val versionUrl = uri"${plainServerUrl(registry)}/mgmt/version"
        val response = basicRequest.get(versionUrl).send()
        assert(response.body === Right("\"0.0.0\""))
      }
    }

  }
}
