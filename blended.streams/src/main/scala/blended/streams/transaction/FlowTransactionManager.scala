package blended.streams.transaction

import blended.util.logging.Logger

import scala.concurrent.Future
import scala.util.{Failure, Try}

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
  def findTransaction(tid : String) : Future[Option[FlowTransaction]]

  /**
    * Best effort to delete a transaction by it's id
    */
  def removeTransaction(tid : String) : Unit

  /**
    * Best effort to remove all known transactions from the container.
    */
  def clearTransactions() : Future[Int]  = withAll{ t => removeTransaction(t.tid) ; true }

  /**
    * A stream of all known transactions of the container.
    */
  def withAll(f : FlowTransaction => Boolean) : Future[Int]

  /**
    * All the completed transaction known to the container.
    */
  def withCompleted(f : FlowTransaction => Unit) : Future[Int] =
    withTransactions(_.state == FlowTransactionStateCompleted)(f)

  /**
    * All the failed transaction known to the container.
    */
  def withFailed(f : FlowTransaction => Unit) : Future[Int] =
    withTransactions(_.state == FlowTransactionStateFailed)(f)

  def withOpen(f : FlowTransaction => Unit) : Future[Int] =
    withTransactions(t => t.state == FlowTransactionStateStarted || t.state == FlowTransactionStateUpdated)(f)

  def withTransactions(select : FlowTransaction => Boolean)(f : FlowTransaction => Unit) : Future[Int] = withAll{ t =>
    if (select(t)) {
      f(t)
      true
    } else {
      false
    }
  }

  def cleanUp(states : FlowTransactionState*) : Future[Int]

  def cleanUp() : Future[Int] =
    cleanUp(FlowTransactionStateStarted, FlowTransactionStateUpdated, FlowTransactionStateFailed, FlowTransactionStateCompleted)
}

