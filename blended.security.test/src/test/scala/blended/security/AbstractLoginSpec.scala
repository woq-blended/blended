package blended.security

import blended.security.internal.SecurityActivator
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.security.auth.Subject
import javax.security.auth.login.LoginContext
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.util.Try

abstract class AbstractLoginSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.security" -> new SecurityActivator()
  )

  def login(user: String, password : String) : Try[Subject] =  Try {
    val lc = new LoginContext("Test", new PasswordCallbackHandler(user, password.toCharArray()))
    lc.login()
    lc.getSubject()
  }
}
