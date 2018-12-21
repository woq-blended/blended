package blended.activemq.client.internal

import blended.activemq.client.ConnectionVerifier
import blended.jms.utils.IdAwareConnectionFactory

import scala.concurrent.{ExecutionContext, Future}

class DefaultConnectionVerifier extends ConnectionVerifier {
  override def verifyConnection(cf: IdAwareConnectionFactory)(implicit eCtxt : ExecutionContext): Future[Boolean] = Future { true }
}
