package blended.security

import java.io.File

import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import javax.security.auth.login.{LoginContext, LoginException}
import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory

class ConfigLoginSpec extends FreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val log = LoggerFactory.getLogger(classOf[ConfigLoginSpec])
  private[this] val baseDir = new File(BlendedTestSupport.projectTestOutput, "simple").getAbsolutePath()

  "The Simple Login Module should" - {

    "allow a user to login and determine the group memberships" in {

      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>

          val lc : LoginContext = new LoginContext("Test", new PasswordCallbackHandler("andreas", "mysecret".toCharArray()))
          lc.login()

          val sub = lc.getSubject()

          val ref = sr.getServiceReference(classOf[BlendedPermissionManager].getName())
          val mgr = sr.getService(ref).asInstanceOf[BlendedPermissionManager]

          val groups = mgr.permissions(sub)

          groups.size should be(2)
          groups should contain("admins")
          groups should contain("blended")
        }
      }
    }

    "deny a login for an unknown user" in {

      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>

          val lc : LoginContext = new LoginContext("Test", new PasswordCallbackHandler("foo", "bar".toCharArray()))
          a [LoginException] should be thrownBy lc.login()
        }
      }
    }

    "deny a login for a known user using wrong credentials" in {

      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>

          val lc : LoginContext = new LoginContext("Test", new PasswordCallbackHandler("andreas", "bar".toCharArray()))
          a [LoginException] should be thrownBy lc.login()
        }
      }
    }

  }
}
