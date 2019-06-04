package blended.mgmt.rest.internal

import java.io.File
import java.util.UUID

import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.repo.WritableArtifactRepo
import blended.mgmt.repo.internal.ArtifactRepoActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM, TestFile}
import blended.updater.config.json.PrickleProtocol._
import blended.updater.config.{ActivateProfile, OverlayConfig, OverlayConfigCompanion, UpdateAction}
import blended.updater.remote.internal.RemoteUpdaterActivator
import blended.util.logging.Logger
import com.softwaremill.sttp
import com.softwaremill.sttp.UriContext
import com.typesafe.config.ConfigFactory
import domino.DominoActivator
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import prickle.{Pickle, Unpickle}

@RequiresForkedJVM
class CollectorServicePojosrSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with TestFile {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.mgmt.repo" -> new ArtifactRepoActivator(),
    "blended.mgmt.rest" -> new MgmtRestActivator(),
    "blended.updater.remote" -> new RemoteUpdaterActivator(),
    "blended.persistence.h2" -> new H2Activator()
  )

  private[this] val log = Logger[this.type]

  case class Server(serviceRegistry : BlendedPojoRegistry, dir : File)

  def withServer(f : Server => Unit) : Unit = {
    log.info(s"Server path: ${baseDir}")

    // cleanup potential left over data from previous runs
    deleteRecursive(
      new File(baseDir, "data"),
      new File(baseDir, "repositories")
    )
    // We consume services with a nice domino API
    new DominoActivator() {
      whenBundleActive {
        whenServicePresent[WritableArtifactRepo] { repo =>
          whenAdvancedServicePresent[HttpContext]("(prefix=mgmt)") { httpCtxt =>
            log.info("Test-Server up and running. Starting test case...")
            f(Server(serviceRegistry = registry, dir = new File(baseDir)))
          }
        }
      }
    }.start(registry.getBundleContext())
  }

  implicit val sttpBackend = sttp.HttpURLConnectionBackend()
  val serverUrl = uri"http://localhost:9995/mgmt"

  val versionUrl = uri"${serverUrl}/version"

  s"REST-API with a self-hosted HTTP server at ${serverUrl}" - {

    s"GET ${versionUrl} should return the version" in {
      withServer { sr =>
        val response = sttp.sttp.get(versionUrl).send()
        assert(response.body === Right("\"0.0.0\""))
      }
    }

    "OverlayConfig" - {

      val url = uri"${serverUrl}/overlayConfig"

      "GET without credentials should fail with 401 - pending until feature is enabled" in logException {
        // we currently do not require any permission for GET
        pending
        withServer { server =>
          val response = sttp.sttp.get(url).send()
          assert(response.code === 401)
        }
      }

      "initial GET should return empty overlay list" in logException {
        withServer { server =>
          val response = sttp.sttp.get(url)
            .auth.basic("tester", "mysecret")
            .send()
          assert(response.code === 200)
          val ocs = Unpickle[Seq[OverlayConfig]].fromString(response.unsafeBody).get
          assert(ocs.size === 0)
        }
      }

      "POST allows upload of new OverlayConfig" in logException {
        val o1 =
          """name = "jvm-medium"
            |version = "1"
            |properties = {
            |  "blended.launcher.jvm.xms" = "768M"
            |  "blended.launcher.jvm.xmx" = "768M"
            |  "amq.systemMemoryLimit" = "500m"
            |}
            |""".stripMargin

        val oc = OverlayConfigCompanion.read(ConfigFactory.parseString(o1)).get

        withServer { server =>
          val responsePost = sttp.sttp
            .post(url)
            .body(Pickle.intoString(oc))
            .header(sttp.HeaderNames.ContentType, sttp.MediaTypes.Json)
            .auth.basic("tester", "mysecret")
            .send()
          assert(responsePost.code === 200)
          assert(responsePost.unsafeBody === "\"Registered jvm-medium-1\"")

          val responseGet = sttp.sttp.get(url)
            .auth.basic("tester", "mysecret")
            .send()
          assert(responseGet.code === 200)
          val ocs = Unpickle[Seq[OverlayConfig]].fromString(responseGet.unsafeBody).get
          assert(ocs.size === 1)
          assert(ocs.find(_.name == "jvm-medium").isDefined)
        }
      }

    }

    "ActivateProfile" - {
      val ci1 = "ci1_ActivateProfile"
      val ci2 = "ci2_ActivateProfile"
      def url(containerId : String) = uri"${serverUrl}/container/${containerId}/update"

      val ap = ActivateProfile(
        id = UUID.randomUUID().toString(),
        profileName = "p",
        profileVersion = "1",
        overlays = Set.empty
      )

      "POST with missing credentials fails with 401 Unauthorized" in logException {
        withServer { server =>
          val responsePost = sttp.sttp
            .post(url(ci1))
            .body(Pickle.intoString(ap))
            .header(sttp.HeaderNames.ContentType, sttp.MediaTypes.Json)
            .send()
          assert(responsePost.code === 401)
          assert(responsePost.statusText === "Unauthorized")
        }
      }

      "POST an valid ActivateProfile action succeeds" in logException {

        withServer { server =>
          val responsePost = sttp.sttp
            .post(url(ci1))
            .body(Pickle.intoString[UpdateAction](ap))
            .header(sttp.HeaderNames.ContentType, sttp.MediaTypes.Json)
            .auth.basic("tester", "mysecret")
            .send()
          log.info(s"Response: ${responsePost}")
          assert(responsePost.code === 200)
          assert(responsePost.unsafeBody === "\"Added UpdateAction to ci1_ActivateProfile\"")
        }
      }
    }

    "Upload deployment pack" - {

      val uploadUrl = uri"${serverUrl}/profile/upload/deploymentpack/artifacts"
      val emptyPackFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.empty-1.0.0.zip")
      val packFile = new File(BlendedTestSupport.projectTestOutput, "test.pack.minimal-1.0.0.zip")

      s"Uploading with missing credentials should fail with 401" in logException {
        withServer { server =>
          assert(packFile.exists())

          val response = sttp.sttp.
            multipartBody(sttp.multipartFile("file", emptyPackFile)).
            post(uploadUrl).
            send()
          assert(response.code === 401)
        }
      }

      s"Uploading with wrong credentials should fail with 401" in logException {
        withServer { server =>
          assert(packFile.exists())

          val response = sttp.sttp.
            multipartBody(sttp.multipartFile("file", emptyPackFile)).
            auth.basic("unknown", "pass").
            post(uploadUrl).
            send()
          assert(response.code === 401)
        }
      }

      s"Multipart POST with empty profile (no bundles) should fail with validation errors" in logException {
        withServer { server =>
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

      s"Multipart POST with minimal profile (one bundles) should succeed" in logException {
        withServer { server =>
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
}
