package blended.security

import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojosrBlendedContainer}
import javax.security.auth.Subject
import javax.security.auth.login.LoginContext
import org.apache.felix.connect.launch.PojoServiceRegistry
import org.scalatest.{FreeSpec, Matchers}

import scala.util.Try

abstract class AbstractLoginSpec extends FreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  val baseDir : String

  def withSecuredContainer [T](f: BlendedPojoRegistry => T): T = {
    withSimpleBlendedContainer(baseDir) { sr =>
      withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>
        f(sr)
      }
    }
  }

  def login(user: String, password : String) : Try[Subject] =  Try {
    val lc = new LoginContext("Test", new PasswordCallbackHandler(user, password.toCharArray()))
    lc.login()
    lc.getSubject()
  }

  def permissionManager(sr: PojoServiceRegistry) : BlendedPermissionManager = {
    val ref = sr.getServiceReference(classOf[BlendedPermissionManager].getName())
    sr.getService(ref).asInstanceOf[BlendedPermissionManager]
  }
}
