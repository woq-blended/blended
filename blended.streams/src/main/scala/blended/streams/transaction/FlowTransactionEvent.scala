package blended.streams.transaction

import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty, TextFlowMessage}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.util.Try

object FlowHeaderConfig {

  private val prefixPath = "prefix"
  private val transIdPath = "transactionId"
  private val branchIdPath = "branchId"
  private val statePath = "transactionState"
  private val trackTransactionPath = "trackTransaction"

  val header : String => String => String = prefix => name => prefix + name

  def create(cfg: Config): FlowHeaderConfig = {
    val prefix = cfg.getString(prefixPath, "Blended")
    val headerTrans = cfg.getString(transIdPath, "TransactionId")
    val headerBranch = cfg.getString(branchIdPath, "BranchId")
    val headerState = cfg.getString(statePath, "TransactionState")
    val headerTrack = cfg.getString(trackTransactionPath, "TrackTransaction")

    FlowHeaderConfig(
      prefix = prefix,
      headerTrans = header(prefix)(headerTrans),
      headerBranch = header(prefix)(headerBranch),
      headerState = header(prefix)(headerState),
      headerTrack = header(prefix)(headerTrack)
    )
  }
}

case class FlowHeaderConfig(
  prefix : String,
  headerTrans : String = "TransactionId",
  headerBranch : String = "BranchId",
  headerState : String = "TransactionState",
  headerTrack : String = "TrackTransaction"
)

object FlowTransactionEvent {

  class InvalidTransactionEnvelopeException(msg: String) extends Exception(msg)

  val event2envelope : FlowHeaderConfig => FlowTransactionEvent => FlowEnvelope = { cfg => event =>

    val basicProps : FlowTransactionEvent => FlowMessageProps = event =>
      event.properties ++ FlowMessage.props(
        cfg.headerTrans -> event.transactionId,
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
            cfg.headerTrans -> update.transactionId,
            cfg.headerState -> state,
            cfg.headerBranch -> branchIds
          ).get
        ), update.transactionId)
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
            FlowTransactionCompleted(id, envelope.flowMessage.header)

          case FlowTransactionState.Failed =>
            val reason : Option[String] = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => Some(txtMsg.content)
              case _ => None
            }
            FlowTransactionFailed(id, envelope.flowMessage.header, reason)

          case FlowTransactionState.Updated =>

            val branchIds : Seq[String] = envelope.header[String](cfg.headerBranch) match {
              case Some(s) => if (s.isEmpty()) Seq() else s.split(",")
              case None => Seq()
            }

            val updatedState : WorklistState = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => WorklistState.withName(txtMsg.content)
              case m => throw new InvalidTransactionEnvelopeException(s"Expected TextFlowMessage for an update envelope, actual [${m.getClass().getName()}]")
            }

            FlowTransactionUpdate(id, envelope.flowMessage.header, updatedState, branchIds:_*)

          case s =>
            throw new InvalidTransactionEnvelopeException(s"Invalid Transaction state in envelope [$s]")
        }
        case (_,_) => throw new InvalidTransactionEnvelopeException(s"Envelope must have headers [${cfg.headerTrans}] and [${cfg.headerState}]")
      }
    }
  }
}

sealed trait FlowTransactionEvent {
  def transactionId : String
  def properties : FlowMessageProps
  def state : FlowTransactionState
}

case class FlowTransactionStarted(
  override val transactionId : String,
  override val properties : Map[String, MsgProperty[_]]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Started
}

case class FlowTransactionUpdate(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  updatedState : WorklistState,
  branchIds : String*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Updated
}

case class FlowTransactionFailed(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  reason : Option[String]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Failed
}

final case class FlowTransactionCompleted (
  override val transactionId : String,
  override val properties : FlowMessageProps
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Completed
}
