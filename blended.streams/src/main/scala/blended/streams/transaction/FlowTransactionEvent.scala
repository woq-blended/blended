package blended.streams.transaction

import blended.container.context.api.ContainerIdentifierService
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty, TextFlowMessage}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

object FlowHeaderConfig {

  // these are the keys the we will look up in the config to potentially
  // overwrite the default settings
  private val prefixPath = "prefix"
  private val transIdPath = "transactionId"
  private val branchIdPath = "branchId"
  private val statePath = "transactionState"
  private val trackTransactionPath = "trackTransaction"
  private val trackSourcePath = "trackSource"
  private val retryCountPath = "retryCount"
  private val maxRetriesPath = "maxRetries"
  private val retryTimeoutPath = "retryTimeout"
  private val retryDestPath = "retryDestination"
  private val firstRetryPath = "firstRetry"
  private val transShardPath = "transactionShard"

  private val transId = "TransactionId"
  private val transShard = "TransactionShard"
  private val branchId = "BranchId"
  private val transState = "TransactionState"
  private val trackTrans = "TrackTransaction"
  private val trackSource = "TrackSource"
  private val retryCount = "RetryCount"
  private val maxRetries = "MaxRetries"
  private val retryTimeout = "RetryTimeout"
  private val retryDest = "RetryDestination"
  private val firstRetry = "FirstRetry"

  val headerConfigPath : String = "blended.flow.header"
  val header : String => String => String = prefix => name => prefix + name

  def create(idSvc : ContainerIdentifierService) : FlowHeaderConfig = create(
    idSvc.containerContext.getContainerConfig().getConfig(FlowHeaderConfig.headerConfigPath)
  )

  def create(prefix : String) : FlowHeaderConfig = FlowHeaderConfig(
    prefix = prefix,
    headerTransId = header(prefix)(transId),
    headerTransShard = header(prefix)(transShard),
    headerBranch = header(prefix)(branchId),
    headerState = header(prefix)(transState),
    headerTrack = header(prefix)(trackTrans),
    headerTrackSource = header(prefix)(trackSource),
    headerRetryCount = header(prefix)(retryCount),
    headerMaxRetries = header(prefix)(maxRetries),
    headerRetryTimeout = header(prefix)(retryTimeout),
    headerRetryDestination = header(prefix)(retryDest),
    headerFirstRetry = header(prefix)(firstRetry)
  )

  def create(cfg : Config) : FlowHeaderConfig = {

    val prefix = cfg.getString(prefixPath, "Blended")
    val headerTransId = cfg.getString(transIdPath, transId)
    val headerTransShard = cfg.getString(transShardPath, transShard)
    val headerBranch = cfg.getString(branchIdPath, branchId)
    val headerState = cfg.getString(statePath, transState)
    val headerTrack = cfg.getString(trackTransactionPath, trackTrans)
    val headerTrackSource = cfg.getString(trackSourcePath, trackSource)
    val headerRetryCount = cfg.getString(retryCountPath, retryCount)
    val headerMaxRetries = cfg.getString(maxRetriesPath, maxRetries)
    val headerRetryTimeout = cfg.getString(retryTimeoutPath, retryTimeout)
    val headerRetryDest = cfg.getString(retryDestPath, retryDest)
    val headerFirstRetry = cfg.getString(firstRetryPath, firstRetry)

    FlowHeaderConfig(
      prefix = prefix,
      headerTransId = header(prefix)(headerTransId),
      headerTransShard = header(prefix)(headerTransShard),
      headerBranch = header(prefix)(headerBranch),
      headerState = header(prefix)(headerState),
      headerTrack = header(prefix)(headerTrack),
      headerTrackSource = header(prefix)(headerTrackSource),
      headerRetryCount = header(prefix)(headerRetryCount),
      headerMaxRetries = header(prefix)(headerMaxRetries),
      headerRetryTimeout = header(prefix)(headerRetryTimeout),
      headerRetryDestination = header(prefix)(headerRetryDest),
      headerFirstRetry = header(prefix)(headerFirstRetry)
    )
  }
}

case class FlowHeaderConfig private (
  prefix : String,
  headerTransId : String = "TransactionId",
  headerTransShard : String = "TransactionShard",
  headerBranch : String = "BranchId",
  headerState : String = "TransactionState",
  headerTrack : String = "TrackTransaction",
  headerTrackSource : String = "TrackSource",
  headerRetryCount : String = "RetryCount",
  headerMaxRetries : String = "MaxRetries",
  headerRetryTimeout : String = "RetryTimeout",
  headerRetryDestination : String = "RetryDestination",
  headerFirstRetry : String = "FirstRetry"
)

object FlowTransactionEvent {

  class InvalidTransactionEnvelopeException(msg : String) extends Exception(msg)

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
          case None    => FlowEnvelope(FlowMessage(basicProps(failed)), failed.transactionId)
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
        case (Some(id), Some(state)) => FlowTransactionState.withName(state) match {
          case FlowTransactionState.Started =>
            val header = envelope.flowMessage.header.filter { case (k, _) => !k.startsWith("JMS") }
            FlowTransactionStarted(id, header)

          case FlowTransactionState.Completed =>
            FlowTransactionCompleted(id, envelope.flowMessage.header)

          case FlowTransactionState.Failed =>
            val reason : Option[String] = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => Some(txtMsg.content)
              case _                        => None
            }
            FlowTransactionFailed(id, envelope.flowMessage.header, reason)

          case FlowTransactionState.Updated =>

            val branchIds : Seq[String] = envelope.header[String](cfg.headerBranch) match {
              case Some(s) => if (s.isEmpty()) Seq() else s.split(",")
              case None    => Seq()
            }

            val updatedState : WorklistState = envelope.flowMessage match {
              case txtMsg : TextFlowMessage => WorklistState.withName(txtMsg.content)
              case m                        => throw new InvalidTransactionEnvelopeException(s"Expected TextFlowMessage for an update envelope, actual [${m.getClass().getName()}]")
            }

            FlowTransactionUpdate(id, envelope.flowMessage.header, updatedState, branchIds : _*)

          case s =>
            throw new InvalidTransactionEnvelopeException(s"Invalid Transaction state in envelope [$s]")
        }
        case (_, _) => throw new InvalidTransactionEnvelopeException(s"Envelope must have headers [${cfg.headerTransId}] and [${cfg.headerState}]")
      }
    }
  }
}

sealed trait FlowTransactionEvent {
  def transactionId : String
  def properties : FlowMessageProps
  def state : FlowTransactionState

  override def toString : String = s"${getClass().getSimpleName()}[$state][$transactionId][$properties]"
}

case class FlowTransactionStarted(
  override val transactionId : String,
  override val properties : Map[String, MsgProperty]
) extends FlowTransactionEvent {
  override val state : FlowTransactionState = FlowTransactionState.Started
}

case class FlowTransactionUpdate(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  updatedState : WorklistState,
  branchIds : String*
) extends FlowTransactionEvent {
  override val state : FlowTransactionState = FlowTransactionState.Updated

  override def toString : String = super.toString + s",branchIds=[${branchIds.mkString(",")}],updatedState=[$updatedState]"
}

case class FlowTransactionFailed(
  override val transactionId : String,
  override val properties : FlowMessageProps,
  reason : Option[String]
) extends FlowTransactionEvent {
  override val state : FlowTransactionState = FlowTransactionState.Failed

  override def toString : String = super.toString + s"[${reason.getOrElse("")}]"
}

final case class FlowTransactionCompleted(
  override val transactionId : String,
  override val properties : FlowMessageProps
) extends FlowTransactionEvent {
  override val state : FlowTransactionState = FlowTransactionState.Completed
}
