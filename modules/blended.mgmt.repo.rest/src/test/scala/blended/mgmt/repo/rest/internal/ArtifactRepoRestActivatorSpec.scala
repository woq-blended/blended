package blended.mgmt.repo.rest.internal

import java.io.File

import scala.concurrent.duration._

import blended.akka.http.HttpContext
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.repo.ArtifactRepo
import blended.mgmt.repo.file.FileArtifactRepo
import blended.security.{BlendedPermissionManager, BlendedPermissions}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.security.auth.Subject
import org.osgi.framework.BundleActivator

class ArtifactRepoRestActivatorSpec extends SimplePojoContainerSpec with LoggingFreeSpecLike with PojoSrTestHelper {

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    bundleName -> new ArtifactRepoRestActivator()
  )

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  val bundleName = classOf[ArtifactRepoRestActivator].getPackage().getName().replaceAll("[.]internal", "")

  s"The ${bundleName}" - {
    "should (un)register http routes for each artifact repository in the registry" in {
      // check we don't have any http context around
      ensureServiceMissing[HttpContext](registry)()(implicitly, 1.second)

      // repo 1 is already present
      val repo1 = new FileArtifactRepo("repo1", new File(baseDir, "repo1"))
      val repo1Reg = registry.registerService(Array(classOf[ArtifactRepo].getName()), repo1, new java.util.Hashtable[String,Any]())

      // our dependency: register a single dummy permission manager
      ensureServiceMissing[BlendedPermissionManager](registry)()(implicitly, 1.second)
      val dummyPermissionManager = new BlendedPermissionManager {
        override def permissions(subject: Subject): BlendedPermissions = BlendedPermissions()
      }
      registry.registerService(Array(classOf[BlendedPermissionManager].getName()), dummyPermissionManager, new java.util.Hashtable[String,Any]())

      // test
      mandatoryService[HttpContext](registry)
      repo1Reg.unregister()
      ensureServiceMissing[HttpContext](registry)()(implicitly, 1.second)

      // repo 2 will be registered after we already started
      val repo2 = new FileArtifactRepo("repo2", new File(baseDir, "repo2"))
      val repo2Reg = registry.registerService(Array(classOf[ArtifactRepo].getName()), repo2, new java.util.Hashtable[String,Any]())

      // the actual test for proper registration
      mandatoryService[HttpContext](registry)
      repo2Reg.unregister()
      ensureServiceMissing[HttpContext](registry)()(implicitly, 1.second)
    }
  }

}
