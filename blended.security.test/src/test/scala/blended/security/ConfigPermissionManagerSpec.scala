package blended.security

import java.io.File
import blended.testsupport.BlendedTestSupport
import scala.concurrent.duration._

class ConfigPermissionManagerSpec extends AbstractLoginSpec {

  private implicit val timeout = 3.seconds
  override val baseDir = new File(BlendedTestSupport.projectTestOutput, "permissions").getAbsolutePath()

  "The ConfigPermissionManager" - {

    def assertPermissions(bp : BlendedPermissions, required : BlendedPermission*) : Unit = {

      def permission(p : BlendedPermissions, permissionClass : String) : Option[BlendedPermission] =
        p.granted.find(_.permissionClass == Some(permissionClass))

      required.foreach { r =>
        permission(bp, r.permissionClass.get) match {
          case None => fail(s"Expected permission of permissionClass [${r.permissionClass.get}]")
          case Some(singlePerm) =>
            assert(singlePerm.properties.size == r.properties.size)
            r.properties.map { p =>
              val pValues = singlePerm.properties.getOrElse(p._1, Seq.empty)
              val values = p._2
              assert(pValues.sorted === values.sorted)
            }
        }
      }
    }

    "should map the JAAS groups to permissions" in {

      val mgr = mandatoryService[BlendedPermissionManager](registry)(None)

      assertPermissions(
        mgr.permissions(login("andreas", "mysecret").get),
        BlendedPermission(Some("container"), Map.empty),
        BlendedPermission(Some("rollout"), Map.empty)
      )

      assertPermissions(
        mgr.permissions(login("tobias", "secret").get),
        BlendedPermission(Some("container"), Map.empty),
        BlendedPermission(Some("rollout"), Map("country" -> List("de")))
      )
    }

    "should merge configured permissions correctly" in {

      val mgr = mandatoryService[BlendedPermissionManager](registry)(None)

      assertPermissions(
        mgr.permissions(login("john", "secret").get),
        BlendedPermission(Some("rollout"), Map("country" -> List("de", "bg")))
      )

      assertPermissions(
        mgr.permissions(login("tommy", "secret").get),
        BlendedPermission(Some("rollout"), Map.empty)
      )
    }
  }
}
