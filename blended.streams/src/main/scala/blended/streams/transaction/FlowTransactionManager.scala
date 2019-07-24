package blended.streams.transaction

import blended.util.logging.Logger

import scala.util.Try

/**
  * Manage the flow transactions known within the container.
  */
trait FlowTransactionManager {

  private val log : Logger = Logger[FlowTransactionManager]
  val config : FlowTransactionManagerConfig

  /**
    * Update a FlowTransaction.
    */
  def updateTransaction(e : FlowTransactionEvent) : Try[FlowTransaction]

  /**
    * Find a flow transaction by it's transaction id.
    */
  def findTransaction(tid : String) : Try[Option[FlowTransaction]]

  /**
    * Best effort to delete a transaction by it's id
    */
  def removeTransaction(tid : String) : Unit

  /**
    * Best effort to remove all known transactions from the container.
    */
  def clearTransactions() : Unit = transactions.foreach{ t => removeTransaction(t.tid) }

  /**
    * A stream of all known transactions of the container.
    */
  def transactions : Iterator[FlowTransaction]

  /**
    * All the completed transaction known to the container.
    */
  def completed : Iterator[FlowTransaction] = listTransactions(_.state == FlowTransactionStateCompleted)

  /**
    * All the failed transaction known to the container.
    */
  def failed : Iterator[FlowTransaction] = listTransactions(_.state == FlowTransactionStateFailed)

  def open : Iterator[FlowTransaction] = listTransactions(t => t.state == FlowTransactionStateStarted || t.state == FlowTransactionStateUpdated)

  def listTransactions(f : FlowTransaction => Boolean) : Iterator[FlowTransaction] = transactions.filter(f)

  def cleanUp(states : FlowTransactionState*) : Unit

  def cleanUp() : Unit =
    cleanUp(FlowTransactionStateStarted, FlowTransactionStateUpdated, FlowTransactionStateFailed, FlowTransactionStateCompleted)
}

