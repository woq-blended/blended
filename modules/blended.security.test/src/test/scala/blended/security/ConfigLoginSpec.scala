package blended.security

import java.io.File

import blended.testsupport.BlendedTestSupport

class ConfigLoginSpec extends AbstractLoginSpec {

  override val baseDir : String = new File(BlendedTestSupport.projectTestOutput, "simple").getAbsolutePath()

  "The Simple Login Module should" - {

    "allow a user to login and determine the group memberships" in {
      val mgr = mandatoryService[BlendedPermissionManager](registry, None)
      val sub = login("andreas", "mysecret")

      val groups = mgr.permissions(sub.get)

      groups.granted.size should be(2)
      assert(groups.granted.exists(_.permissionClass.contains("admins")))
      assert(groups.granted.exists(_.permissionClass.contains("blended")))
    }

    "deny a login for an unknown user" in {
      assert(login("foo", "bar").isFailure)
    }

    "deny a login for a known user using wrong credentials" in {
      assert(login("andreas", "bar").isFailure)
    }
  }
}
