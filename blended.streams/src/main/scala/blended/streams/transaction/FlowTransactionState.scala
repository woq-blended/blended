package blended.streams.transaction

import scala.language.implicitConversions

object FlowTransactionState extends Enumeration {
  type FlowTransactionState = Value
  val Started, Updated, Completed, Failed = Value
}

object EventSeverity extends Enumeration {
  type EventSeverity = Value
  val Unknown, Information, Harmless, Warning, Minor, Critical, Fatal = Value

  // scalastyle:off magic.number
  implicit def severityToInt(severity : EventSeverity) : Int = severity match {
    case Unknown     => 0
    case Information => 10
    case Harmless    => 20
    case Warning     => 30
    case Minor       => 40
    case Critical    => 50
    case Fatal       => 60
  }
  // scalastyle:off magic.number
}
