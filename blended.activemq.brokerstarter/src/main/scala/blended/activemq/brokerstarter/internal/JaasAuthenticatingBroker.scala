package blended.activemq.brokerstarter.internal

import java.security.Principal
import java.security.cert.X509Certificate
import java.util

import blended.security.PasswordCallbackHandler
import blended.security.boot.GroupPrincipal
import blended.util.logging.Logger
import javax.security.auth.Subject
import javax.security.auth.login.LoginContext
import org.apache.activemq.broker.{Broker, ConnectionContext}
import org.apache.activemq.command.ConnectionInfo
import org.apache.activemq.security.{AbstractAuthenticationBroker, SecurityContext}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * A broker that performs authentication against an arbitrary blended login module
 */
class JaasAuthenticatingBroker(parent : Broker, brokerCfg : BrokerConfig) extends AbstractAuthenticationBroker(parent) {

  private val log : Logger = Logger[JaasAuthenticatingBroker]

  @throws[Exception]
  override def addConnection(context: ConnectionContext, info: ConnectionInfo): Unit = {

    // First we check if we need to create a new Security context or can reuse the existing one
    Option(context.getSecurityContext()) match {
      case None =>
        log.debug(s"Authenticating user [${info.getUserName()}] for broker [$getBrokerName()]")
        val secCtxt : SecurityContext = authenticate(info.getUserName(), info.getPassword(), null)
        context.setSecurityContext(secCtxt)
        securityContexts.add(secCtxt)
      case Some(_) =>
    }

    // At this point we either have thrown an exception or we do have a security context set
    Try {
      super.addConnection(context, info)
    } match {
      case Success(_) => // do nothing
      case Failure(e) =>
        log.warn(s"Failed to add connection for user [${info.getUserName()}] in broker [$getBrokerName()] : [${e.getMessage()}]")
        securityContexts.remove(context.getSecurityContext())
        context.setSecurityContext(null)
        throw e
    }
  }

  @throws[SecurityException]
  override def authenticate(username: String, password: String, peerCertificates: Array[X509Certificate]): SecurityContext = {

    (Option(username), Option(password)) match {
      case (Some(u), _) =>
        log.info(s"Trying to authenticate [$username] for broker [${getBrokerName()}]")
        try {
          val lc = new LoginContext("Test", new PasswordCallbackHandler(u, Option(password).map(_.toCharArray()).getOrElse(Array.empty)))
          lc.login()
          val subj : Subject = lc.getSubject()
          val g : Set[Principal] = subj.getPrincipals().asScala.filter(_.isInstanceOf[GroupPrincipal]).toSet
          new SecurityContext(u) {
            override def getPrincipals: util.Set[Principal] = g.asJava
          }
        } catch {
          case NonFatal(t) =>
            log.warn(s"Error logging in [$u] to [${brokerCfg.brokerName}] : [${t.getMessage()}]")
            throw new SecurityException(t)
        }
      case (None, _) =>
        brokerCfg.anonymousUser match {
          case Some(u) =>
            log.info(s"Authenticating anonymous user name [$u] to broker [${getBrokerName()}] with groups [${brokerCfg.anonymousGroups}]")
            val groups : Set[Principal] = brokerCfg.anonymousGroups.map(g => new GroupPrincipal(g)).toSet
            new SecurityContext(u) {
              override def getPrincipals: util.Set[Principal] = groups.asJava
            }
          case None =>
            throw new SecurityException(s"Anonymous access to broker [${getBrokerName()}] is disabled")
        }
    }
  }
}
