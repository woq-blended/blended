package blended.mgmt.rest.internal

import java.io.File
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.repo.WritableArtifactRepo
import blended.mgmt.repo.internal.ArtifactRepoActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.BlendedPojoRegistry
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.pojosr.SimplePojosrBlendedContainer
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.remote.internal.RemoteUpdaterActivator
import blended.util.logging.Logger
import com.softwaremill.sttp
import com.softwaremill.sttp.UriContext
import domino.DominoActivator
import org.scalatest.Matchers

class ContainerDeploymentSpec
  extends LoggingFreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val log = Logger[this.type]

  case class Server(serviceRegistry: BlendedPojoRegistry, dir: File)

  def withServer(f: Server => Unit): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    log.info("Starting and waiting...")
    val fut = Future {
      val serverDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsoluteFile()
      withSimpleBlendedContainer(serverDir.getPath()) { sr =>
        withStartedBundles(sr)(Seq(
          "blended.akka" -> Some(() => new BlendedAkkaActivator()),
          "blended.akka.http" -> Some(() => new BlendedAkkaHttpActivator()),
          "blended.security" -> Some(() => new SecurityActivator()),
          "blended.mgmt.repo" -> Some(() => new ArtifactRepoActivator()),
          "blended.mgmt.rest" -> Some(() => new MgmtRestActivator()),
          "blended.updater.remote" -> Some(() => new RemoteUpdaterActivator()),
          "blended.persistence.h2" -> Some(() => new H2Activator())
        )) { sr =>
          var ok = false
          val waiter = new Thread("Wait for finish") {
            override def run(): Unit = {
              Thread.sleep(20000)
              interrupt()
            }
          }
          waiter.start()
          // We consume services with a nice domino API
          new DominoActivator() {
            whenBundleActive {
              whenServicePresent[WritableArtifactRepo] { repo =>
                whenAdvancedServicePresent[HttpContext]("(prefix=mgmt)") { httpCtxt =>
                  log.info("Test-Server up and running. Starting test case...")
                  f(Server(serviceRegistry = sr, dir = serverDir))
                  ok = true
                }
              }
            }
          }.start(sr.getBundleContext())
          if (!ok) {
            // Wait for waiter thread
            log.info("Waiting for timeout...	")
            waiter.join()
          }
        }
      }
    }
    Await.result(fut, FiniteDuration(20, TimeUnit.SECONDS))
  }

  implicit val sttpBackend = sttp.HttpURLConnectionBackend()
  val serverUrl = uri"http://localhost:9995/mgmt"

  val versionUrl = uri"${serverUrl}/version"

  s"GET ${versionUrl} should return the version" in {
    withServer { sr =>
      val response = sttp.sttp.get(versionUrl).send()
      assert(response.body === Right("\"0.0.0\""))
    }
  }

  "Upload deployment pack" - {

    val uploadUrl = uri"${serverUrl}/profile/upload/deploymentpack/artifacts"

    s"Multipart POST ${uploadUrl} with empty profile (no bundles) should fail with validation errors" in {
      withServer { server =>
        val emptyPackFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.empty-1.0.0.zip")
        assert(emptyPackFile.exists() === true)

        val response = sttp.sttp.
          multipartBody(sttp.multipartFile("file", emptyPackFile)).
          auth.basic("tester", "mysecret").
          post(uploadUrl).
          send()

        log.info("body: " + response.body)
        log.info("headers: " + response.headers)
        log.info("response: " + response)

        assert(response.code === 422)
        assert(response.statusText === "Unprocessable Entity")
        assert(response.body.isLeft)
        assert(response.body.left.get ===
          "Could not process the uploaded deployment pack file. Reason: requirement failed: " +
          "A ResolvedRuntimeConfig needs exactly one bundle with startLevel '0', but this one has (distinct): 0")
      }
    }

    s"Multipart POST ${uploadUrl} with minimal profile (one bundles) should succeed" in {
      withServer { server =>
        val packFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.minimal-1.0.0.zip")
        assert(packFile.exists() === true)

        val response = sttp.sttp.
          multipartBody(sttp.multipartFile("file", packFile)).
          auth.basic("tester", "mysecret").
          post(uploadUrl).
          send()

        log.info("body: " + response.body)
        log.info("headers: " + response.headers)
        log.info("response: " + response)

        assert(response.code === 200)
        assert(response.statusText === "OK")
        assert(response.body.isRight)
        assert(response.body.right.get === "\"Uploaded profile test.pack.minimal 1.0.0\"")

        // We expect the bundle file in the local repo
        assert(new File(server.dir, "repositories/artifacts/org/example/fake/1.0.0/fake-1.0.0.jar").exists())
        
        // We expect the profile in the profile repo
        assert(new File(server.dir, "repositories/rcs/test.pack.minimal-1.0.0.conf").exists())
      }
    }

  }

}
