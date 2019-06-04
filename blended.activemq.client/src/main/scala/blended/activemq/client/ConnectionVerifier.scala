package blended.activemq.client

import blended.jms.utils.IdAwareConnectionFactory

import scala.concurrent.{ExecutionContext, Future}

trait ConnectionVerifierFactory {
  def createConnectionVerifier() : ConnectionVerifier
}

trait ConnectionVerifier {
  def verifyConnection(cf : IdAwareConnectionFactory)(implicit eCtxt : ExecutionContext) : Future[Boolean]
}

trait VerificationFailedHandler {
  def verificationFailed(cf : IdAwareConnectionFactory) : Unit
}

