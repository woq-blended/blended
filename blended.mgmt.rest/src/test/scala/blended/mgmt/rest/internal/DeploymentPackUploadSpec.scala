package blended.mgmt.rest.internal

import java.io.File

import scala.collection.{ immutable => sci }
import scala.util.Try

import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.prickle.akka.http.PrickleSupport
import blended.security.BlendedPermissionManager
import blended.security.akka.http.DummyBlendedSecurityDirectives
import blended.updater.config.ContainerInfo
import blended.updater.config.ContainerRegistryResponseOK
import blended.updater.config.OverlayConfig
import blended.updater.config.RemoteContainerState
import blended.updater.config.RuntimeConfig
import blended.updater.config.UpdateAction
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import javax.security.auth.Subject
import blended.security.BlendedPermissions
import blended.testsupport.TestFile
import blended.testsupport.TestFile.DeletePolicy
import blended.testsupport.TestFile.DeleteWhenNoFailure
import blended.testsupport.TestFile.DeletePolicy

class DeploymentPackUploadSpec
  extends FreeSpec
  with Matchers
  with ScalatestRouteTest
  with TestFile {

  implicit val delete: DeletePolicy = DeleteWhenNoFailure

  "test" in {

    object TestCollector extends CollectorService with DummyBlendedSecurityDirectives with PrickleSupport {
      def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit = ???
      def getCurrentState(): sci.Seq[RemoteContainerState] = ???
      def getOverlayConfigs(): sci.Seq[OverlayConfig] = ???
      def getRuntimeConfigs(): sci.Seq[RuntimeConfig] = ???
      def installBundle(repoId: String, path: String, file: File, sha1Sum: Option[String]): Try[Unit] = ???
      val mgr: BlendedPermissionManager = new BlendedPermissionManager {
        def permissions(subject: Subject): BlendedPermissions = BlendedPermissions(List())
      }
      def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = ???
      def registerOverlayConfig(oc: OverlayConfig): Unit = ???
      def registerRuntimeConfig(rc: RuntimeConfig): Unit = ???
      def version: String = ???
    }

    val multipartForm = Multipart.FormData(
      Multipart.FormData.BodyPart.Strict(
        "file",
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, "2,3,5\n7,11,13,17,23\n29,31,37\n"),
        Map("filename" -> "primes.csv")
      )
    )

    withTestDir() { dir =>
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
    }

    pending

    Post("/", multipartForm) ~> TestCollector.httpRoute ~> check {
      status shouldEqual StatusCodes.OK
    }

  }

}