package blended.security

import java.io.File

import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{ PojoSrTestHelper, SimplePojosrBlendedContainer }
import blended.util.logging.Logger
import javax.security.auth.login.LoginContext
import org.scalatest.{ DoNotDiscover, FreeSpec }

@DoNotDiscover
class LDAPLoginSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val user = "andreas"
  private[this] val pwd = "mysecret"

  private[this] val log = Logger[LDAPLoginSpec]

  "the security activator should" - {

    "initialise the Login Module correctly" in {

      val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

      withSimpleBlendedContainer(baseDir) { sr =>

        withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>

          val lc : LoginContext = new LoginContext("Test", new PasswordCallbackHandler(user, pwd.toCharArray()))
          lc.login()

          val sub = lc.getSubject()

          val ref = sr.getServiceReference(classOf[BlendedPermissionManager].getName())
          val mgr = sr.getService(ref).asInstanceOf[BlendedPermissionManager]

          val groups = mgr.permissions(sub)

          assert(groups.size == 2)
          assert(groups.contains("admins"))
          assert(groups.contains("blended"))
        }
      }
    }
  }
}
