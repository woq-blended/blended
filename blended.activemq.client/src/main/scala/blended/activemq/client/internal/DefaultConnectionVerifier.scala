package blended.activemq.client.internal

import blended.activemq.client.{ConnectionVerifier, ConnectionVerifierFactory}
import blended.jms.utils.IdAwareConnectionFactory

import scala.concurrent.{ExecutionContext, Future}

class DefaultConnectionVerifierFactory extends ConnectionVerifierFactory {
  override def createConnectionVerifier() : ConnectionVerifier = new DefaultConnectionVerifier()
}

class DefaultConnectionVerifier extends ConnectionVerifier {
  override def verifyConnection(cf : IdAwareConnectionFactory)(implicit eCtxt : ExecutionContext) : Future[Boolean] = Future { true }
}
