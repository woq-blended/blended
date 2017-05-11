package blended.file

import com.typesafe.config.Config

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object FilePollConfig {

  val PATH_ID           = "id"
  val PATH_INTERVAL     = "interval"
  val PATH_SOURCEDIR    = "sourceDirectory"
  val PATH_PATTERN      = "pattern"
  val PATH_BACKUP       = "backup"
  val PATH_HEADER       = "header"
  val PATH_LOCK         = "lock"
  val PATH_ASTEXT       = "asText"
  val PATH_TMP_EXT      = "extension"

  def apply(cfg: Config) = {
    val props : Map[String, String] = if (cfg.hasPath(PATH_HEADER)) {
      val builder = new mutable.MapBuilder[String, String, mutable.Map[String,String]](mutable.Map.empty)

      val headerIterator = cfg.getConfig(PATH_HEADER).entrySet().iterator()
      while (headerIterator.hasNext) {
        val headerEntry = headerIterator.next()

        val key = headerEntry.getKey
        val value = cfg.getConfig(PATH_HEADER).getString(key)

        builder += (key -> value)
      }
      builder.result().toMap
    } else {
      Map.empty
    }

    new FilePollConfig(
      id = cfg.getString(PATH_ID),
      interval = if (cfg.hasPath(PATH_INTERVAL)) cfg.getInt(PATH_INTERVAL).seconds else 1.second,
      sourceDir = cfg.getString(PATH_SOURCEDIR),
      pattern= if (cfg.hasPath(PATH_PATTERN)) Some(cfg.getString(PATH_PATTERN)) else None,
      lock = if (cfg.hasPath(PATH_LOCK)) Some(cfg.getString(PATH_LOCK)) else None,
      backup = if (cfg.hasPath(PATH_BACKUP)) Some(cfg.getString(PATH_BACKUP)) else None,
      header = props,
      asText = if (cfg.hasPath(PATH_ASTEXT)) cfg.getBoolean(PATH_ASTEXT) else false,
      tmpExt = if (cfg.hasPath(PATH_TMP_EXT)) cfg.getString(PATH_TMP_EXT) else "_to_send"
    )
  }
}

case class FilePollConfig(
  id : String,
  interval : FiniteDuration,
  sourceDir : String,
  pattern : Option[String],
  lock: Option[String],
  backup : Option[String],
  header: Map[String, String],
  asText: Boolean,
  tmpExt : String
)
