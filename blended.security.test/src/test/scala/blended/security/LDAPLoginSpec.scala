package blended.security

import java.io.File

import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import javax.security.auth.login.LoginContext
import org.osgi.framework.BundleActivator
import org.scalatest.DoNotDiscover

import scala.concurrent.duration._

@DoNotDiscover
abstract class LDAPLoginSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  private[this] val user = "andreas"
  private[this] val pwd = "mysecret"
  private[this] val log = Logger[LDAPLoginSpec]

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.security" -> new SecurityActivator()
  )

  "the security activator should" - {

    "initialise the Login Module correctly" in {

      val lc : LoginContext = new LoginContext("Test", new PasswordCallbackHandler(user, pwd.toCharArray()))
      lc.login()

      val sub = lc.getSubject()

      implicit val to = timeout
      val mgr = mandatoryService[BlendedPermissionManager](registry, None)

      val groups = mgr.permissions(sub)

      assert(groups.granted.size == 2)
      assert(groups.granted.exists(_.permissionClass == Some("admins")))
      assert(groups.granted.exists(_.permissionClass == Some("blended")))
    }
  }

}
