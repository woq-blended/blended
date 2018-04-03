package blended.security.akka.http

import java.util

import blended.security.{GroupPrincipal, UserPrincipal}
import javax.security.auth.Subject
import javax.security.auth.callback.{CallbackHandler, NameCallback, PasswordCallback}
import javax.security.auth.login.{FailedLoginException, LoginException}
import javax.security.auth.spi.LoginModule

class DummyLoginModule extends LoginModule {
  private[this] val log = org.log4s.getLogger

  private[this] var subject : Option[Subject] = None
  private[this] var cbHandler : Option[CallbackHandler] = None
  private[this] var succeeded : Boolean = false

  override def initialize(subject: Subject,
    callbackHandler: CallbackHandler,
    sharedState: util.Map[String, _],
    options: util.Map[String, _])
  : Unit = {

    log.debug("Initialising LDAP Login module ...")
    this.subject = Option(subject)
    this.cbHandler = Option(callbackHandler)
  }

  override def login(): Boolean = {
    val nameCallback = new NameCallback("name:")
    val passwordCallback = new PasswordCallback("password:", false)

    cbHandler match {
      case None => throw new LoginException("No Callback Handler defined")
      case Some(cbh) => try {
        cbh.handle(Array(nameCallback, passwordCallback))

        val name = nameCallback.getName()
        val pwd = new String(passwordCallback.getPassword())
        log.info(s"Logging in user [$name]")

        if (name == "root" && pwd == "mysecret") {
          succeeded = true

          subject.foreach{ s =>
            s.getPrincipals().add(new UserPrincipal(name))
            s.getPrincipals().add(new GroupPrincipal("hawtio"))
          }
          succeeded
        } else {
          throw new FailedLoginException("Boom")
        }
      } catch {
        case t : Throwable =>
          log.error(t)(t.getMessage())
          throw new LoginException(t.getMessage())
      }
    }

  }

  override def commit(): Boolean = succeeded

  override def abort(): Boolean = false

  override def logout(): Boolean = false
}
