package blended.security

import java.io.File

import blended.testsupport.BlendedTestSupport

class ConfigLoginSpec extends AbstractLoginSpec {

  override val baseDir = new File(BlendedTestSupport.projectTestOutput, "simple").getAbsolutePath()

  "The Simple Login Module should" - {

    "allow a user to login and determine the group memberships" in {

      withSecuredContainer[Unit] { sr =>

        val sub = login("andreas", "mysecret")
        val  mgr = permissionManager(sr)

        val groups = mgr.permissions(sub.get)

        groups.granted.size should be(2)
        assert(groups.granted.exists(_.permissionClass == Some("admins")))
        assert(groups.granted.exists(_.permissionClass == Some("blended")))
      }
    }

    "deny a login for an unknown user" in {

      withSecuredContainer[Unit] { sr =>
        assert(login("foo", "bar").isFailure)
      }
    }

    "deny a login for a known user using wrong credentials" in {

      withSecuredContainer[Unit] { sr =>
        assert(login("andreas", "bar").isFailure)
      }

    }
  }
}
