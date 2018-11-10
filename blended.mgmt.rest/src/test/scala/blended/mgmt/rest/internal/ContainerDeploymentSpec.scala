package blended.mgmt.rest.internal

import java.io.File

import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.repo.internal.ArtifactRepoActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.updater.remote.internal.RemoteUpdaterActivator
import blended.util.logging.Logger
import com.softwaremill.sttp
import com.softwaremill.sttp.UriContext
import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._

@RequiresForkedJVM
class ContainerDeploymentSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with BeforeAndAfterAll {

  private[this] val log = Logger[this.type]

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.mgmt.repo" -> new ArtifactRepoActivator(),
    "blended.mgmt.rest" -> new MgmtRestActivator(),
    "blended.updater.remote" -> new RemoteUpdaterActivator(),
    "blended.persistence.h2" -> new H2Activator()
  )

  override protected def afterAll(): Unit = stopRegistry(registry)

  private implicit val sttpBackend = sttp.HttpURLConnectionBackend()
  private val serverUrl = uri"http://localhost:9995/mgmt"
  private val versionUrl = uri"${serverUrl}/version"

  private def withServer(f : () => Unit): Unit = {

    implicit val timeout = 3.seconds
    val mgmtCtxt = mandatoryService[HttpContext](registry)(Some("(prefix=mgmt)"))
    f()
  }


  s"GET ${versionUrl} should return the version" in {
    withServer { () =>
      val response = sttp.sttp.get(versionUrl).send()
      assert(response.body === Right("\"0.0.0\""))
    }
  }

  "Upload deployment pack" - {

    val uploadUrl = uri"${serverUrl}/profile/upload/deploymentpack/artifacts"

    s"Multipart POST ${uploadUrl} with empty profile (no bundles) should fail with validation errors" in {
      withServer { () =>
        val emptyPackFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.empty-1.0.0.zip")
        assert(emptyPackFile.exists() === true)

        val response = sttp.sttp.
          multipartBody(sttp.multipartFile("file", emptyPackFile)).
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
      withServer { () =>
        val packFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.minimal-1.0.0.zip")
        assert(packFile.exists() === true)

        val response = sttp.sttp.
          multipartBody(sttp.multipartFile("file", packFile)).
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
        assert(new File(baseDir, "repositories/artifacts/org/example/fake/1.0.0/fake-1.0.0.jar").exists())
        
        // We expect the profile in the profile repo
        assert(new File(baseDir, "repositories/rcs/test.pack.minimal-1.0.0.conf").exists())
      }
    }

  }

}
