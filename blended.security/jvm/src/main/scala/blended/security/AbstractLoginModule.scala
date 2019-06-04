package blended.security

import java.util

import blended.container.context.api.ContainerIdentifierService
import blended.security.boot.{GroupPrincipal, UserPrincipal}
import blended.security.internal.BlendedConfiguration
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}
import javax.security.auth.Subject
import javax.security.auth.callback.{CallbackHandler, NameCallback, PasswordCallback}
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

import scala.reflect.ClassTag

abstract class AbstractLoginModule extends LoginModule {

  private[this] val log = Logger[AbstractLoginModule]

  protected var subject : Option[Subject] = None
  protected var cbHandler : Option[CallbackHandler] = None
  protected var loginConfig : Config = ConfigFactory.empty()
  protected var loggedInUser : Option[String] = None
  protected var idSvc : Option[ContainerIdentifierService] = None

  protected val moduleName : String

  override def initialize(
    subject : Subject,
    callbackHandler : CallbackHandler,
    sharedState : util.Map[String, _],
    options : util.Map[String, _]
  ) : Unit = {

    def getOption[T](name : String)(implicit classTag : ClassTag[T]) : Option[T] =
      Option(options.get(name)) match {
        case Some(v) if classTag.runtimeClass.isAssignableFrom(v.getClass) =>
          Some(v.asInstanceOf[T])

        case Some(v) =>
          log.warn(s"Expected configuration object [$name] of type [${classOf[Config].getName()}], got [${v.getClass().getName()}]")
          None

        case None => None
      }

    log.info(s"Initialising Login module ...[$moduleName]")

    loginConfig = getOption[Config](BlendedConfiguration.configProp).getOrElse(ConfigFactory.empty())
    idSvc = getOption[ContainerIdentifierService](BlendedConfiguration.idSvcProp)

    // This is the subject which needs to be enriched with the user and group information
    this.subject = Option(subject)

    // This is the callback handler passed in to determine the username and password
    this.cbHandler = Option(callbackHandler)
  }

  @throws[LoginException]
  protected def extractCredentials() : (String, String) = {
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
  override def commit() : Boolean = {
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

  @throws[LoginException]
  override def abort() : Boolean = {
    loggedInUser = None
    postAbort()
    true
  }

  @throws[LoginException]
  override def logout() : Boolean = {
    loggedInUser = None
    postLogout()
    true
  }

  protected def getGroups(user : String) : List[String]

  protected def postLogin() : Unit = {}
  protected def postAbort() : Unit = {}
  protected def postLogout() : Unit = {}
  protected def postCommit() : Unit = {}
}
