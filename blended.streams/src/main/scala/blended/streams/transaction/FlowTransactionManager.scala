package blended.streams.transaction

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.scaladsl.Source

import scala.util.Try

/**
  * Manage the flow transactions known within the container.
  */
trait FlowTransactionManager {

  /**
    * Update a FlowTransaction.
    */
  def updateTransaction(e : FlowTransactionEvent) : Try[FlowTransaction]

  /**
    * Find a flow transaction by it's transaction id.
    */
  def findTransaction(tid : String) : Try[Option[FlowTransaction]]

  /**
    * Delete a transaction by it's id
    */
  def removeTransaction(tid : String) : Try[Option[FlowTransaction]]

  /**
    * A stream of all known transactions of the container.
    */
  def transactions : Source[FlowTransaction, NotUsed]
}

object FlowTransactionManagerActor {
  def props(mgr : FlowTransactionManager) : Props = Props(new FlowTransactionManagerActor(mgr))
}

class FlowTransactionManagerActor(mgr : FlowTransactionManager) extends Actor {

  override def receive: Receive = {
    case (event : FlowTransactionEvent, respondTo : ActorRef) =>
      respondTo ! mgr.updateTransaction(event)
  }
}
