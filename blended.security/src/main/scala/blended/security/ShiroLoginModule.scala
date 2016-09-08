package blended.security

import java.util
import javax.security.auth.Subject
import javax.security.auth.callback.{CallbackHandler, NameCallback, PasswordCallback}
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.util.ThreadContext
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object ShiroLoginModule {

  private[this] var secMgr : Option[org.apache.shiro.mgt.SecurityManager] = None

  def setSecurityManager(mgr: org.apache.shiro.mgt.SecurityManager) : Unit = secMgr = Option(mgr)

  def securityManager : org.apache.shiro.mgt.SecurityManager = {
    require(secMgr.isDefined)
    secMgr.get
  }
}

class ShiroLoginModule extends LoginModule {

  import ShiroLoginModule.securityManager

  private[this] val log = LoggerFactory.getLogger(classOf[ShiroLoginModule])
  private[this] var subject : Subject = null
  private[this] var cbHandler : CallbackHandler = null

  private[this] var shared : Map[String, _] = Map.empty
  private[this] var succeeded : Boolean = false

  override def initialize(
    subj: Subject,
    callbackHandler: CallbackHandler,
    sharedState: util.Map[String, _], options:
    util.Map[String, _]): Unit =
  {
    log.info("Initializing Shiro login module.")

    require(Option(callbackHandler).isDefined)
    require(Option(subj).isDefined)

    shared = Option(sharedState) match {
      case None => Map.empty
      case Some(s) => s.asScala.toMap
    }

    subject = subj
    cbHandler = callbackHandler
  }

  override def logout(): Boolean = false

  override def abort(): Boolean = false

  override def commit(): Boolean = succeeded

  override def login(): Boolean = {

    val nameCallback = new NameCallback("name:")
    val passwordCallback = new PasswordCallback("password:", false)

    try {
      cbHandler.handle(Array(nameCallback, passwordCallback))
    } catch {
      case e: Exception => throw new LoginException(e.getMessage())
    }

    val name = nameCallback.getName()
    val pwd = passwordCallback.getPassword()

    log.debug(s"Logging in [${name}]")

    ThreadContext.bind(securityManager)

    val shiroSubject = SecurityUtils.getSubject()
    val token = new UsernamePasswordToken(name, pwd)

    try {
      shiroSubject.login(token)
      val principals = subject.getPrincipals().asScala
      principals.foreach{ p => log.info(s"Found Principal [${p.getClass().getName()}] [$p]") }
    } catch {
      case e: Exception =>
        val msg = s"Shiro login failed (${e.getMessage()})"
        log.error(msg, e)
        succeeded = false
        throw new LoginException(msg)
    }

    succeeded = true

    log.debug(s"Successfully logged in user [${name}] through shiro")

    succeeded
  }
}
