package blended.streams.transaction

import scala.language.implicitConversions
import scala.util.Try

sealed trait FlowTransactionState{
  val name : String
  override def toString: String = name
}

case object FlowTransactionStateStarted extends FlowTransactionState { override val name: String = "Started" }
case object FlowTransactionStateUpdated extends FlowTransactionState { override val name: String = "Updated" }
case object FlowTransactionStateCompleted extends FlowTransactionState { override val name: String = "Completed" }
case object FlowTransactionStateFailed extends FlowTransactionState { override val name: String = "Failed" }

object FlowTransactionState {
  def apply(s: String): Try[FlowTransactionState] = Try {
    s match {
      case FlowTransactionStateStarted.name => FlowTransactionStateStarted
      case FlowTransactionStateUpdated.name => FlowTransactionStateUpdated
      case FlowTransactionStateCompleted.name => FlowTransactionStateCompleted
      case FlowTransactionStateFailed.name => FlowTransactionStateFailed
      case _ => throw new IllegalArgumentException(s"[$s] is not a valid TransactionState")
    }
  }
}

object EventSeverity extends Enumeration {
  type EventSeverity = Value
  val Unknown, Information, Harmless, Warning, Minor, Critical, Fatal = Value

  implicit def severityToInt(severity: EventSeverity): Int = severity match {
    case Unknown       =>  0
    case Information   => 10
    case Harmless      => 20
    case Warning       => 30
    case Minor         => 40
    case Critical      => 50
    case Fatal         => 60
  }
}
