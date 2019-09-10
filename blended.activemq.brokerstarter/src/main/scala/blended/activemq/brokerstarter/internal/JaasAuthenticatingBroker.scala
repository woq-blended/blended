package blended.activemq.brokerstarter.internal

import java.security.cert.X509Certificate

import blended.util.logging.Logger
import org.apache.activemq.broker.{Broker, ConnectionContext}
import org.apache.activemq.command.ConnectionInfo
import org.apache.activemq.security.{AbstractAuthenticationBroker, SecurityContext}

import scala.util.{Failure, Success, Try}

/**
 * A broker that performs authentication against an arbitrary blended login module
 */
class JaasAuthenticatingBroker(parent : Broker) extends AbstractAuthenticationBroker(parent) {

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

    var securityContext = context.getSecurityContext
    if (securityContext == null) {
      securityContext = authenticate(info.getUserName, info.getPassword, null)
      context.setSecurityContext(securityContext)
      securityContexts.add(securityContext)
    }
    try super.addConnection(context, info)
    catch {
      case e: Exception =>
        securityContexts.remove(securityContext)
        context.setSecurityContext(null)
        throw e
    }
  }

  @throws[SecurityException]
  override def authenticate(username: String, password: String, peerCertificates: Array[X509Certificate]): SecurityContext = {
    throw new SecurityException("Jaas Authentication is not yet enabled")
  }
}
