package blended.streams.file

import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowMessage, MsgProperty}
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object FilePollConfig {

  val PATH_ID             = "id"
  val PATH_INTERVAL       = "interval"
  val PATH_SOURCEDIR      = "sourceDirectory"
  val PATH_PATTERN        = "pattern"
  val PATH_BACKUP         = "backup"
  val PATH_BACKUP_TST     = "backupTimestamp"
  val PATH_LOCK           = "lock"
  val PATH_ASTEXT         = "asText"
  val PATH_TMP_EXT        = "extension"
  val PATH_ACKTIMEOUT     = "ackTimeout"
  val PATH_FILENAME_PROP  = "filenameProperty"
  val PATH_FILEPATH_PROP  = "filepathProperty"
  val PATH_BATCHSIZE      = "batchSize"
  val PATH_CHARSET        = "charset"
  val PATH_HEADER         = "header"

  val DEFAULT_BATCH_SIZE : Int = 10

  def apply(cfg : Config, idSvc : ContainerIdentifierService) : FilePollConfig = {

    val props : FlowMessage.FlowMessageProps = if (cfg.hasPath(PATH_HEADER)) {
      cfg.getConfig(PATH_HEADER).entrySet().asScala.map { e =>
        val k = e.getKey()
        val v = idSvc.resolvePropertyString(cfg.getConfig(PATH_HEADER).getString(k, "")).get.toString()
        k -> MsgProperty(v).get
      }.toMap
    } else {
      Map.empty
    }
    apply(cfg, FlowHeaderConfig.create(idSvc), props)
  }

  def apply(cfg : Config, headerCfg : FlowHeaderConfig, header : FlowMessage.FlowMessageProps = Map.empty) : FilePollConfig = {

    new FilePollConfig(
      id = cfg.getString(PATH_ID),
      headerCfg = headerCfg,
      interval = cfg.getDuration(PATH_INTERVAL, 1.second),
      sourceDir = cfg.getString(PATH_SOURCEDIR),
      pattern = cfg.getStringOption(PATH_PATTERN),
      lock = cfg.getStringOption(PATH_LOCK),
      backup = cfg.getStringOption(PATH_BACKUP),
      backupTimestamp = cfg.getBoolean(PATH_BACKUP_TST, true),
      charSet = cfg.getStringOption(PATH_CHARSET),
      ackTimeout = cfg.getDuration(PATH_ACKTIMEOUT, 1.second),
      asText = cfg.getBoolean(PATH_ASTEXT, false),
      tmpExt = cfg.getString(PATH_TMP_EXT, "_to_send"),
      filenameProp = cfg.getString(PATH_FILENAME_PROP, "BlendedFileName"),
      filepathProp = cfg.getString(PATH_FILEPATH_PROP, "BlendedFilePath"),
      batchSize = cfg.getInt(PATH_BATCHSIZE, DEFAULT_BATCH_SIZE),
      header = header
    )
  }
}

case class FilePollConfig(
  id : String,
  headerCfg : FlowHeaderConfig,
  interval : FiniteDuration,
  sourceDir : String,
  pattern : Option[String],
  lock : Option[String],
  backup : Option[String],
  backupTimestamp : Boolean,
  asText: Boolean,
  charSet : Option[String],
  ackTimeout : FiniteDuration,
  batchSize : Int,
  filenameProp : String,
  filepathProp : String,
  tmpExt : String,
  header : FlowMessage.FlowMessageProps
)
