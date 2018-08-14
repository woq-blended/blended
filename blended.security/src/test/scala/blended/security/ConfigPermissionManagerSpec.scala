package blended.security

import java.io.File

import blended.testsupport.BlendedTestSupport

class ConfigPermissionManagerSpec extends AbstractLoginSpec {

  override val baseDir = new File(BlendedTestSupport.projectTestOutput, "permissions").getAbsolutePath()

  "The ConfigPermissionManager" - {

    def assertPermissions(bp: BlendedPermissions, required : BlendedPermission*) : Unit = {

      def permission(p: BlendedPermissions, permissionClass: String) : Option[BlendedPermission] =
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

      withSecuredContainer[Unit] { sr =>

        val mgr = permissionManager(sr)

        assertPermissions(
          mgr.permissions(login("andreas", "mysecret").get),
          BlendedPermission(Some("container"), Map.empty),
          BlendedPermission(Some("rollout"), Map.empty)
        )

        assertPermissions(
          mgr.permissions(login("tobias", "secret").get),
          BlendedPermission(Some("container"), Map.empty),
          BlendedPermission(Some("rollout"), Map("country" -> Seq("de")))
        )
      }
    }

    "should merge configured permissions correctly" in {
      withSecuredContainer[Unit] { sr =>

        val mgr = permissionManager(sr)

        assertPermissions(
          mgr.permissions(login("john", "secret").get),
          BlendedPermission(Some("rollout"), Map("country" -> Seq("de", "bg")))
        )

        assertPermissions(
          mgr.permissions(login("tommy", "secret").get),
          BlendedPermission(Some("rollout"), Map.empty)
        )
      }
    }
  }
}
