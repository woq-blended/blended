package blended.streams.transaction

import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty, TextFlowMessage}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import com.typesafe.config.Config

import scala.util.Try

object FlowHeaderConfig {

  private val prefixPath = "prefix"
  private val transIdPath = "transactionId"
  private val branchIdPath = "branchId"
  private val statePath = "transactionState"

  def create(cfg: Config): FlowHeaderConfig = {
    val prefix = cfg.getString(prefixPath)
    val headerTrans = cfg.getString(transIdPath)
    val headerBranch = cfg.getString(branchIdPath)
    val headerState = cfg.getString(statePath)

    FlowHeaderConfig(
      prefix = prefix,
      headerTrans = headerTrans,
      headerBranch = headerBranch,
      headerState = headerState
    )
  }
}

case class FlowHeaderConfig(
  prefix : String,
  headerTrans : String,
  headerBranch : String,
  headerState : String
)

object FlowTransactionEvent {

  class InvalidTransactionEnvelopeException(msg: String) extends Exception(msg)

  val event2envelope : FlowHeaderConfig => FlowTransactionEvent => FlowEnvelope = { cfg => event =>

    val basicProps : FlowTransactionEvent => FlowMessageProps = event =>
        Map[String, MsgProperty[_]](
          cfg.headerTrans -> event.transactionId,
          cfg.headerState -> event.state.toString()
        )

    event match {
      case started : FlowTransactionStarted =>
        FlowEnvelope(FlowMessage(
          basicProps(started)
        )).withHeaders(started.creationProperties.filterKeys(k => !k.startsWith("JMS"))).get

      case completed : FlowTransactionCompleted =>
        FlowEnvelope(FlowMessage(basicProps(completed)))

      case failed : FlowTransactionFailed =>
        failed.reason match {
          case None => FlowEnvelope(FlowMessage(basicProps(failed)))
          case Some(s) => FlowEnvelope(FlowMessage(s, basicProps(failed)))
        }

      case update : FlowTransactionUpdate =>
        val branchIds : String = update.branchIds.mkString(",")
        val state : String = update.state.toString()
        FlowEnvelope(FlowMessage(
          update.updatedState.toString(),
          Map[String, MsgProperty[_]](
            cfg.headerTrans -> update.transactionId,
            cfg.headerState -> state,
            cfg.headerBranch -> branchIds
          )
        ))
    }
  }

  def envelope2event : FlowHeaderConfig => FlowEnvelope => Try[FlowTransactionEvent] = { cfg => envelope =>

    Try {
      (envelope.header[String](cfg.headerTrans), envelope.header[String](cfg.headerState)) match {
        case (Some(id), Some(state)) => FlowTransactionState.withName(state) match {
          case FlowTransactionState.Started =>
            val header = envelope.flowMessage.header.filter{ case (k, v) => !k.startsWith("JMS") }
            FlowTransactionStarted(id, header)

          case FlowTransactionState.Completed =>
            FlowTransactionCompleted(id)

          case FlowTransactionState.Failed =>
            val reason : Option[String] = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => Some(txtMsg.content)
              case _ => None
            }
            FlowTransactionFailed(id, reason)

          case FlowTransactionState.Updated =>

            val branchIds : Seq[String] = envelope.header[String](cfg.headerBranch) match {
              case Some(s) => if (s.isEmpty()) Seq() else s.split(",")
              case None => Seq()
            }

            val updatedState : WorklistState = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => WorklistState.withName(txtMsg.content)
              case m => throw new InvalidTransactionEnvelopeException(s"Expected TextFlowMessage for an update envelope, actual [${m.getClass().getName()}]")
            }

            FlowTransactionUpdate(id, updatedState, branchIds:_*)

          case s =>
            throw new InvalidTransactionEnvelopeException(s"Invalid Transaction state in envelope [$s]")
        }
        case (_,_) => throw new InvalidTransactionEnvelopeException(s"Envelope must have headers [${cfg.headerTrans}] and [${cfg.headerBranch}]")
      }
    }
  }
}

sealed trait FlowTransactionEvent {
  def transactionId : String
  def state : FlowTransactionState
}

case class FlowTransactionStarted(
  override val transactionId : String,
  creationProperties : Map[String, MsgProperty[_]]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Started
}

case class FlowTransactionUpdate(
  override val transactionId : String,
  updatedState : WorklistState,
  branchIds : String*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Updated
}

case class FlowTransactionFailed(
  override val transactionId : String,
  reason : Option[String]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Failed
}

final case class FlowTransactionCompleted (
  override val transactionId : String
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Completed
}