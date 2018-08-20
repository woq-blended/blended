package blended.mgmt.rest.internal

import java.io.File
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.repo.WritableArtifactRepo
import blended.mgmt.repo.internal.ArtifactRepoActivator
import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.TestFile
import blended.testsupport.TestFile.DeletePolicy
import blended.testsupport.TestFile.DeleteWhenNoFailure
import blended.testsupport.pojosr.BlendedPojoRegistry
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.pojosr.SimplePojosrBlendedContainer
import blended.util.logging.Logger
import domino.DominoActivator
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import blended.akka.http.HttpContext
import blended.updater.remote.internal.RemoteUpdaterActivator
import blended.persistence.h2.internal.H2Activator

class ContainerDeploymentSpec
  extends FreeSpec
  with Matchers
  with TestFile
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val log = Logger[this.type]

  implicit val testFileDeletePolicy: DeletePolicy = DeleteWhenNoFailure

  def withServer(f: (BlendedPojoRegistry) => Unit): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    log.info("Starting and waiting...")
    val fut = Future {
      withSimpleBlendedContainer(new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()) { sr =>
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
                  f(sr)
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

  "test" in {
    withServer { sr =>
      withTestDir() { dir =>
        log.info("Creating deployment package")
        writeFile(
          file = new File(dir, "profile.conf"),
          content = """name="test.pack.empty"
                    |version="1.0.0"
                    |bundle=[]
                    |features=[]
                    |startLevel=10
                    |defaultStartLevel=10
                    |frameworkProperties={}
                    |properties={}
                    |systemProperties={}
                    |resources=[]
                    |resolvedFeatures=[]
                    |""".stripMargin
        )

        log.info("Uploading it to server")
        pending
      }
    }

  }

}