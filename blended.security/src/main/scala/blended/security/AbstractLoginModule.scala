package blended.security

import java.util

import blended.security.boot.{GroupPrincipal, UserPrincipal}
import com.typesafe.config.{Config, ConfigFactory}
import javax.security.auth.Subject
import javax.security.auth.callback.{CallbackHandler, NameCallback, PasswordCallback}
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule
import org.slf4j.LoggerFactory

abstract class AbstractLoginModule extends LoginModule {

  private[this] val log = LoggerFactory.getLogger(classOf[AbstractLoginModule])

  protected var subject : Option[Subject] = None
  protected var cbHandler : Option[CallbackHandler] = None
  protected var loginConfig : Config = ConfigFactory.empty()
  protected var loggedInUser : Option[String] = None

  protected val moduleName : String

  override def initialize(
    subject: Subject,
    callbackHandler: CallbackHandler,
    sharedState: util.Map[String, _],
    options: util.Map[String, _])
  : Unit = {

    log.info(s"Initialising Login module ...[$moduleName]")

    options.get("config") match {
      case cfg : Config => loginConfig = cfg
      case other => log.warn(s"Expected configuration object of type [${classOf[Config].getName()}], got [${other.getClass().getName()}]")
    }

    // This is the subject which needs to be enriched with the user and group information
    this.subject = Option(subject)

    // This is the callback handler passed in to determine the username and password
    this.cbHandler = Option(callbackHandler)
  }

  @throws[LoginException]
  protected def extractCredentials() : (String,String) = {
    cbHandler match {
      case None => throw new LoginException(s"No Callback Handler defined for module [$moduleName]")
      case Some(cbh) =>
        val nameCallback = new NameCallback("User: ")
        val passwordCallback = new PasswordCallback("Password: ", false)
        cbh.handle(Array(nameCallback, passwordCallback))

        val user = nameCallback.getName()
        val pwd = new String(passwordCallback.getPassword())
        log.info(s"Authenticating user [$user]")

        (user, pwd)
    }
  }

  @throws[LoginException]
  final def login() : Boolean = {
    try {
      doLogin()
    } finally {
      postLogin()
    }
  }

  @throws[LoginException]
  protected def doLogin() : Boolean

  @throws[LoginException]
  override def commit(): Boolean = {
    try {
      loggedInUser match {
        case None => false
        case Some(u) =>
          subject.foreach { s =>
            s.getPrincipals().add(new UserPrincipal(u))
            val groups = getGroups(u)
            log.debug(s"Found groups [$groups] for [$u]")
            groups.foreach { g =>
              s.getPrincipals().add(new GroupPrincipal(g))
            }
          }
          postCommit()
          true
      }
    }
  }

  @throws[LoginException]
  override def abort(): Boolean = {
    loggedInUser = None
    postAbort()
    true
  }

  @throws[LoginException]
  override def logout(): Boolean = {
    loggedInUser = None
    postLogout()
    true
  }

  protected def getGroups(user: String) : List[String]

  protected def postLogin() = {}
  protected def postAbort() = {}
  protected def postLogout() = {}
  protected def postCommit() = {}
}
