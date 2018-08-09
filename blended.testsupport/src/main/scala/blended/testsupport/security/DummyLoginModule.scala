package blended.testsupport.security

import java.util

import blended.security.boot.{ GroupPrincipal, UserPrincipal }
import blended.util.logging.Logger
import javax.security.auth.Subject
import javax.security.auth.callback.{ CallbackHandler, NameCallback, PasswordCallback }
import javax.security.auth.login.{ FailedLoginException, LoginException }
import javax.security.auth.spi.LoginModule

trait UsersAndGropus {

  val users : Map[String, String] = Map(
    "root" -> "mysecret"
  )

  val groups : Map[String, List[String]] = Map (
    "root" -> List("hawtio")
  )
}

class DummyLoginModule extends LoginModule with UsersAndGropus {
  private[this] val log = Logger[DummyLoginModule]

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
        val realizedSubject = subject.get

        cbh.handle(Array(nameCallback, passwordCallback))

        val name = nameCallback.getName()
        val pwd = new String(passwordCallback.getPassword())
        log.info(s"Logging in user [$name]")

        succeeded = users.get(name) match {
          case Some(storedPwd) => pwd == storedPwd
          case None => false
        }

        if (succeeded) {
          groups.getOrElse(name, List.empty).foreach{ s =>
            realizedSubject.getPrincipals().add(new UserPrincipal(name))
            realizedSubject.getPrincipals().add(new GroupPrincipal(s))
          }
        } else {
          throw new FailedLoginException("Boom")
        }
        succeeded
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
