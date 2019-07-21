package blended.streams.transaction.internal

import akka.NotUsed
import akka.stream.scaladsl.Source
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionManager}

import scala.util.{Failure, Try}

class FileFlowTransactionManager extends FlowTransactionManager {

  /**
    * @inheritdoc
    */
  override def updateTransaction(e: FlowTransactionEvent): Try[FlowTransaction] =
    Failure(new Exception("Boom"))

  /**
    * @inheritdoc
    */
  override def findTransaction(tid: String): Option[FlowTransaction] =
    None

  /**
    * @inheritdoc
    */
  override def removeTransaction(tid: String): Try[Option[FlowTransaction]] =
    Failure(new Exception("Boom"))

  /**
    * @inheritdoc
    */
  override def transactions: Source[FlowTransaction, NotUsed] =
    Source.empty[FlowTransaction]
}
