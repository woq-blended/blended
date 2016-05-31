package blended.security.internal

import java.util
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.spi.LoginModule

import org.slf4j.LoggerFactory

class ShiroLoginModule extends LoginModule {

  private[this] val log = LoggerFactory.getLogger(classOf[ShiroLoginModule])
  private[this] var subject : Option[Subject] = None

  override def initialize(subj: Subject, callbackHandler: CallbackHandler, sharedState: util.Map[String, _], options: util.Map[String, _]): Unit = {
    log.info("Initializing Shiro login module.")
    subject = Some(subj)
  }

  override def logout(): Boolean = {
    require(subject.isDefined)
    log.info(s"Logging out ${subject.get.getPrincipals()}")

    true
  }

  override def abort(): Boolean = {
    require(subject.isDefined)
    log.info(s"Aborting ${subject.get.getPrincipals()}")

    true
  }

  override def commit(): Boolean = {
    require(subject.isDefined)
    log.info(s"Committing ${subject.get.getPrincipals()}")

    true
  }

  override def login(): Boolean = {
    require(subject.isDefined)
    log.info(s"Logging in ${subject.get.getPrincipals()}")

    true
  }
}
