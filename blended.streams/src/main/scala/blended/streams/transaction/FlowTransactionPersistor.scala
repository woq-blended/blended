package blended.streams.transaction

import java.{util => ju}

import blended.persistence.PersistenceService
import blended.streams.message.MsgProperty
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState

import scala.collection.JavaConverters._
import scala.util.Try

class TransactionNotFoundException(id : String)
  extends Exception(s"Transaction [$id] not found in persistence store")

class TransactionIdNotUnique(id : String)
  extends Exception(s"Transaction [$id] is not unique in persistence store")

class FlowTransactionPersistor(pSvc : PersistenceService) {

  private val creationPrefix = "create."
  private val worklistPrefix = "worklist."
  private val fieldPrefix = "transaction."

  private val stateField : String = fieldPrefix + "transactionState"
  private val idField : String = fieldPrefix + "transactionId"

  private val pClass : String = classOf[FlowTransaction].getName()

  private def storeProps(t : FlowTransaction) : ju.Map[String, _ <: Any] = {

    val cProps : Map[String, _ <: Any] = t.creationProps.map {
      case (k, v) =>
        creationPrefix + k -> v.value
    }

    val wlProps : Map[String, _ <: Any] = t.worklist.map {
      case (k, states) =>
        worklistPrefix + k -> states.map(_.toString).mkString(",")
    }

    val stateProps : Map[String, _ <: Any] = Map(
      stateField -> t.state.toString(),
      idField -> t.id
    )

    (cProps ++ wlProps ++ stateProps).asJava
  }

  private def transaction(storeProps : Map[String, _ <: Any]) : Try[FlowTransaction] = Try {

    def property[T](propName : String, props : Map[String, _ <: Any]) : Try[T] = Try {
      props.get(propName).map(_.asInstanceOf[T]).get
    }

    val state : FlowTransactionState = {
      val stateString : String = property[String](stateField, storeProps).get
      FlowTransactionState.withName(stateString)
    }

    val creationProps : Map[String, MsgProperty] =
      storeProps.filterKeys(_.startsWith(creationPrefix)).map {
        case (k, v) =>
          k.substring(creationPrefix.length) -> MsgProperty.lift(v).get
      }.toMap

    val worklist : Map[String, List[WorklistState]] = {
      val wlProps = storeProps.filterKeys(_.startsWith(worklistPrefix))

      wlProps.map {
        case (k, v) =>
          val states : List[WorklistState] =
            v.toString.split(",").map(s => WorklistState.withName(s)).toList

          k.substring(worklistPrefix.length) -> states
      }
    }

    val id : String = property[String](idField, storeProps).get

    FlowTransaction(
      id = id,
      creationProps = creationProps,
      worklist = worklist,
      state = state
    )
  }

  def persistTransaction(t : FlowTransaction) : Try[Unit] = Try {
    pSvc.deleteByExample(pClass, Map(idField -> t.id).asJava)
    pSvc.persist(pClass, storeProps(t))
  }

  def restoreTransaction(id : String) : Try[FlowTransaction] = Try {

    pSvc.findByExample(pClass, Map(idField -> id).asJava) match {
      case Seq()      => throw new TransactionNotFoundException(id)
      case h :: Seq() => transaction(h.asScala.toMap).get
      case _          => throw new TransactionIdNotUnique(id)
    }
  }
}
