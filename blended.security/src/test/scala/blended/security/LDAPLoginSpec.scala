package blended.security

import java.io.File

import blended.security.internal.SecurityActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import javax.security.auth.callback._
import javax.security.auth.login.LoginContext
import org.scalatest.{DoNotDiscover, FreeSpec}

@DoNotDiscover
class LDAPLoginSpec extends FreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val user = "root"
  private[this] val pwd = "mysecret"

  private[this] val log = org.log4s.getLogger

  "the security activator should" - {

    "initialise the Login Module correctly" in {

      val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

      withSimpleBlendedContainer(baseDir) { sr =>

        withStartedBundle(sr)(symbolicName = "blended.security", activator = Some(() => new SecurityActivator())) { sr =>

          val lc : LoginContext = new LoginContext("Test", new TestCallbackHandler(user, pwd))
          lc.login()

          val sub = lc.getSubject()
          log.info(s"Logged in [$sub]")
        }
      }
    }
  }

  private class TestCallbackHandler(name: String, password: String) extends CallbackHandler {

    override def handle(callbacks: Array[Callback]): Unit = {
      callbacks.foreach { cb: Callback =>
        cb match {
          case nameCallback: NameCallback => nameCallback.setName(name)
          case pwdCallback: PasswordCallback => pwdCallback.setPassword(password.toCharArray())
          case other => throw new UnsupportedCallbackException(other, "The submitted callback is not supported")
        }
      }
    }
  }
}
