package blended.streams

import blended.container.context.api.ContainerIdentifierService
import com.typesafe.config.Config
import blended.util.config.Implicits._

object FlowHeaderConfig {

  // these are the keys the we will look up in the config to potentially
  // overwrite the default settings
  private val prefixPath = "prefix"
  private val transIdPath = "transactionId"
  private val branchIdPath = "branchId"
  private val statePath = "transactionState"
  private val createdPath = "transactionCreated"
  private val updatedPath = "transactionUpdated"
  private val trackTransactionPath = "trackTransaction"
  private val trackSourcePath = "trackSource"
  private val retryCountPath = "retryCount"
  private val maxRetriesPath = "maxRetries"
  private val retryTimeoutPath = "retryTimeout"
  private val retryDestPath = "retryDestination"
  private val firstRetryPath = "firstRetry"
  private val transShardPath = "transactionShard"
  private val bridgeVendorPath = "bridgeVendor"
  private val bridgeProviderPath = "bridgeProvider"
  private val keepAlivesMissedPath = "keepAlivesMissed"

  private val transId = "TransactionId"
  private val transShard = "TransactionShard"
  private val branchId = "BranchId"
  private val transState = "TransactionState"
  private val transCreated = "TransactionCreated"
  private val transUpdated = "TransactionUpdated"
  private val trackTrans = "TrackTransaction"
  private val trackSource = "TrackSource"
  private val retryCount = "RetryCount"
  private val maxRetries = "MaxRetries"
  private val retryTimeout = "RetryTimeout"
  private val retryDest = "RetryDestination"
  private val firstRetry = "FirstRetry"
  private val bridgeVendor = "BridgeVendor"
  private val bridgeProvider = "BridgeProvider"
  private val keepAlivesMissed = "KeepAlivesMissed"

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
    headerTransCreated = header(prefix)(transCreated),
    headerTransUpdated = header(prefix)(transUpdated),
    headerTrack = header(prefix)(trackTrans),
    headerTrackSource = header(prefix)(trackSource),
    headerRetryCount = header(prefix)(retryCount),
    headerMaxRetries = header(prefix)(maxRetries),
    headerRetryTimeout = header(prefix)(retryTimeout),
    headerRetryDestination = header(prefix)(retryDest),
    headerFirstRetry = header(prefix)(firstRetry),
    headerBridgeVendor = header(prefix)(bridgeVendor),
    headerBridgeProvider = header(prefix)(bridgeProvider),
    headerKeepAlivesMissed = header(prefix)(keepAlivesMissed)
  )

  def create(cfg: Config): FlowHeaderConfig = {

    val prefix = cfg.getString(prefixPath, "Blended")
    val headerTransId = cfg.getString(transIdPath, transId)
    val headerTransShard = cfg.getString(transShardPath, transShard)
    val headerBranch = cfg.getString(branchIdPath, branchId)
    val headerState = cfg.getString(statePath, transState)
    val headerCreated = cfg.getString(createdPath, transCreated)
    val headerUpdated = cfg.getString(updatedPath, transUpdated)
    val headerTrack = cfg.getString(trackTransactionPath, trackTrans)
    val headerTrackSource = cfg.getString(trackSourcePath, trackSource)
    val headerRetryCount = cfg.getString(retryCountPath, retryCount)
    val headerMaxRetries = cfg.getString(maxRetriesPath, maxRetries)
    val headerRetryTimeout = cfg.getString(retryTimeoutPath, retryTimeout)
    val headerRetryDest = cfg.getString(retryDestPath, retryDest)
    val headerFirstRetry = cfg.getString(firstRetryPath, firstRetry)
    val headerBridgeVendor = cfg.getString(bridgeVendorPath, bridgeVendor)
    val headerBridgeProvider = cfg.getString(bridgeProviderPath, bridgeProvider)
    val headerKeepAlivesMissed = cfg.getString(keepAlivesMissedPath, keepAlivesMissed)

    FlowHeaderConfig(
      prefix = prefix,
      headerTransId = header(prefix)(headerTransId),
      headerTransShard = header(prefix)(headerTransShard),
      headerBranch = header(prefix)(headerBranch),
      headerState = header(prefix)(headerState),
      headerTransCreated = header(prefix)(headerCreated),
      headerTransUpdated = header(prefix)(headerUpdated),
      headerTrack = header(prefix)(headerTrack),
      headerTrackSource = header(prefix)(headerTrackSource),
      headerRetryCount = header(prefix)(headerRetryCount),
      headerMaxRetries = header(prefix)(headerMaxRetries),
      headerRetryTimeout = header(prefix)(headerRetryTimeout),
      headerRetryDestination = header(prefix)(headerRetryDest),
      headerFirstRetry = header(prefix)(headerFirstRetry),
      headerBridgeVendor = header(prefix)(headerBridgeVendor),
      headerBridgeProvider = header(prefix)(headerBridgeProvider),
      headerKeepAlivesMissed = header(prefix)(headerKeepAlivesMissed)
    )
  }
}

case class FlowHeaderConfig private (
  prefix : String,
  headerTransId : String,
  headerTransShard : String,
  headerBranch : String,
  headerState : String,
  headerTransCreated : String,
  headerTransUpdated : String,
  headerTrack : String,
  headerTrackSource : String,
  headerRetryCount : String,
  headerMaxRetries : String,
  headerRetryTimeout : String,
  headerRetryDestination : String,
  headerFirstRetry : String,
  headerBridgeVendor : String,
  headerBridgeProvider : String,
  headerKeepAlivesMissed : String
)
