package blended.file

import blended.container.context.api.ContainerIdentifierService
import blended.streams.transaction.FlowHeaderConfig
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.concurrent.duration._

object FilePollConfig {

  val PATH_ID             = "id"
  val PATH_INTERVAL       = "interval"
  val PATH_SOURCEDIR      = "sourceDirectory"
  val PATH_PATTERN        = "pattern"
  val PATH_BACKUP         = "backup"
  val PATH_LOCK           = "lock"
  val PATH_ASTEXT         = "asText"
  val PATH_TMP_EXT        = "extension"
  val PATH_OP_TIMEOUT     = "operationTimeout"
  val PATH_HANDLE_TIMEOUT = "handleTimeout"
  val PATH_FILENAME_PROP  = "filenameProperty"
  val PATH_FILEPATH_PROP  = "filepathProperty"
  val PATH_BATCHSIZE      = "batchSize"
  val PATH_CHARSET        = "charset"

  def apply(cfg : Config, idSvc : ContainerIdentifierService) : FilePollConfig =
    apply(cfg, FlowHeaderConfig.create(idSvc))

  def apply(cfg: Config, headerCfg : FlowHeaderConfig): FilePollConfig = {

    new FilePollConfig(
      id = cfg.getString(PATH_ID),
      headerCfg = headerCfg,
      interval = cfg.getDuration(PATH_INTERVAL, 1.second),
      sourceDir = cfg.getString(PATH_SOURCEDIR),
      pattern= cfg.getStringOption(PATH_PATTERN),
      lock = cfg.getStringOption(PATH_LOCK),
      backup = cfg.getStringOption(PATH_BACKUP),
      charSet = cfg.getStringOption(PATH_CHARSET),
      asText = cfg.getBoolean(PATH_ASTEXT, false),
      tmpExt = cfg.getString(PATH_TMP_EXT, "_to_send"),
      filenameProp = cfg.getString(PATH_FILENAME_PROP, "BlendedFileName"),
      filepathProp = cfg.getString(PATH_FILEPATH_PROP, "BlendedFilePath"),
      batchSize = cfg.getInt(PATH_BATCHSIZE, FilePollActor.defaultBatchSize),
      operationTimeout = cfg.getDuration(PATH_OP_TIMEOUT, 1.second),
      handleTimeout = cfg.getDuration(PATH_HANDLE_TIMEOUT, 1.second)
    )
  }
}

case class FilePollConfig(
  id : String,
  headerCfg : FlowHeaderConfig,
  interval : FiniteDuration,
  sourceDir : String,
  pattern : Option[String],
  lock: Option[String],
  backup : Option[String],
  asText: Boolean,
  charSet : Option[String],
  operationTimeout : FiniteDuration,
  handleTimeout : FiniteDuration,
  batchSize : Int,
  filenameProp : String,
  filepathProp : String,
  tmpExt : String
)
