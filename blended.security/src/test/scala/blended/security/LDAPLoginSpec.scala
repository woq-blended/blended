package blended.security

import javax.security.auth.callback._
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag
import javax.security.auth.login.{AppConfigurationEntry, Configuration, LoginContext}
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

import scala.collection.JavaConverters._

class LDAPLoginSpec extends FreeSpec
  with BeforeAndAfterAll {

  private[this] val log = org.log4s.getLogger

  class SimpleAppConfiguration extends Configuration {

    private[this] val options : Map[String, String] = Map.empty

    override def getAppConfigurationEntry(name: String): Array[AppConfigurationEntry] = {

      val entry = new AppConfigurationEntry(classOf[LDAPLoginModule].getName(), LoginModuleControlFlag.SUFFICIENT, options.asJava)

      Array(entry)
    }
  }

  class TestCallbackHandler(name: String, password: String) extends CallbackHandler {

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

  override protected def beforeAll(): Unit = {
    Configuration.setConfiguration(new SimpleAppConfiguration())
  }

  "The LDAP login module should" - {

    "allow a login" in {
      val lc : LoginContext = new LoginContext("Test", new TestCallbackHandler("andreas", "test"))
      lc.login()

      val sub = lc.getSubject()

      log.info(s"Logged in [$sub]")
    }
  }

}
