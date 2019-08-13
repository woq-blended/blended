package blended.streams.transaction

import blended.streams.FlowHeaderConfig
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty, TextFlowMessage}
import blended.streams.worklist.WorklistState

import scala.util.Try

object FlowTransactionEvent {

  class InvalidTransactionEnvelopeException(msg: String) extends Exception(msg)

  val event2envelope : FlowHeaderConfig => FlowTransactionEvent => FlowEnvelope = { cfg => event =>

    val basicProps : FlowTransactionEvent => FlowMessageProps = event =>
      event.properties ++ FlowMessage.props(
        cfg.headerTransId -> event.transactionId,
        cfg.headerState -> event.state.toString()
      ).get

    event match {
      case started : FlowTransactionStarted =>
        FlowEnvelope(FlowMessage(
          basicProps(started)
        ), started.transactionId).withHeaders(started.properties.filterKeys(k => !k.startsWith("JMS"))).get

      case completed : FlowTransactionCompleted =>
        FlowEnvelope(FlowMessage(basicProps(completed)), completed.transactionId)

      case failed : FlowTransactionFailed =>
        failed.reason match {
          case None => FlowEnvelope(FlowMessage(basicProps(failed)), failed.transactionId)
          case Some(s) => FlowEnvelope(FlowMessage(s)(basicProps(failed)), failed.transactionId)
        }

      case update : FlowTransactionUpdate =>
        val branchIds : String = update.branchIds.mkString(",")
        val state : String = update.state.toString()
        FlowEnvelope(FlowMessage(
          update.updatedState.toString()
        )(
          update.properties ++ FlowMessage.props(
            cfg.headerTransId -> update.transactionId,
            cfg.headerState -> state,
            cfg.headerBranch -> branchIds
          ).get
        ), update.transactionId)
    }
  }

  def envelope2event : FlowHeaderConfig => FlowEnvelope => Try[FlowTransactionEvent] = { cfg => envelope =>

    Try {
      (envelope.header[String](cfg.headerTransId), envelope.header[String](cfg.headerState)) match {
        case (Some(id), Some(state)) => FlowTransactionState.apply(state).get match {
          case FlowTransactionStateStarted =>
            val header = envelope.flowMessage.header.filter{ case (k, v) => !k.startsWith("JMS") }
            FlowTransactionStarted(id, header)

          case FlowTransactionStateCompleted =>
            FlowTransactionCompleted(id, envelope.flowMessage.header)

          case FlowTransactionStateFailed =>
            val reason : Option[String] = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => Some(txtMsg.content)
              case _ => None
            }
            FlowTransactionFailed(id, envelope.flowMessage.header, reason)

          case FlowTransactionStateUpdated =>

            val branchIds : Seq[String] = envelope.header[String](cfg.headerBranch) match {
              case Some(s) => if (s.isEmpty()) Seq() else s.split(",")
              case None => Seq()
            }

            val updatedState : WorklistState = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => WorklistState.apply(txtMsg.content).get
              case m => throw new InvalidTransactionEnvelopeException(s"Expected TextFlowMessage for an update envelope, actual [${m.getClass().getName()}]")
            }

            FlowTransactionUpdate(id, envelope.flowMessage.header, updatedState, branchIds:_*)

          case s =>
            throw new InvalidTransactionEnvelopeException(s"Invalid Transaction state in envelope [$s]")
        }
        case (_,_) => throw new InvalidTransactionEnvelopeException(s"Envelope must have headers [${cfg.headerTransId}] and [${cfg.headerState}]")
      }
    }
  }
}

sealed trait FlowTransactionEvent {
  def transactionId : String
  def properties : FlowMessageProps
  def state : FlowTransactionState

  override def toString: String = s"${getClass().getSimpleName()}[$state][$transactionId][$properties]"
}

case class FlowTransactionStarted(
  override val transactionId : String,
  override val properties : Map[String, MsgProperty]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionStateStarted
}

case class FlowTransactionUpdate(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  updatedState : WorklistState,
  branchIds : String*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionStateUpdated

  override def toString: String = super.toString + s",branchIds=[${branchIds.mkString(",")}],updatedState=[$updatedState]"
}

case class FlowTransactionFailed(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  reason : Option[String]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionStateFailed

  override def toString: String = super.toString + s"[${reason.getOrElse("")}]"
}

final case class FlowTransactionCompleted (
  override val transactionId : String,
  override val properties : FlowMessageProps
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionStateCompleted
}
