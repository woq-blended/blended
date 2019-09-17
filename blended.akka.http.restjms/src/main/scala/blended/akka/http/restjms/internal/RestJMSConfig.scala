package blended.akka.http.restjms.internal

import blended.util.config.Implicits._
import com.typesafe.config.Config
import scala.collection.JavaConverters._

object RestJMSConfig {

  val operationsPath = "operations"

  def fromConfig(cfg : Config) : RestJMSConfig = {

    val operations : Map[String, JmsOperationConfig] = if (cfg.hasPath(operationsPath)) {
      cfg.getObject(operationsPath).keySet().asScala.map { key =>
        (key, JmsOperationConfig(cfg.getConfig(operationsPath).getConfig(s""""$key"""")))
      }.toMap
    } else {
      Map.empty
    }

    RestJMSConfig(operations)
  }
}

case class RestJMSConfig(
  // These are the configured operations
  operations : Map[String, JmsOperationConfig]
)

object JmsOperationConfig {
  private val destinationPath = "destination"
  private val headerPath = "header"
  private val timeoutPath = "timeout"
  private val jmsReplyPath = "jmsreply"
  private val cTypePath = "contentTypes"
  private val isSoapPath = "isSoap"
  private val encodingPath = "encoding"

  //noinspection NameBooleanParameters
  def apply(cfg : Config) : JmsOperationConfig = {


    new JmsOperationConfig(
      destination = cfg.getString(destinationPath),
      //scalastyle:off magic.number
      timeout = cfg.getLong(timeoutPath, 1000L),
      //scalastyle:on magic.number

      header = if (cfg.hasPath(headerPath)) {
        cfg.getObject(headerPath).keySet().asScala.map { key =>
          (key, cfg.getObject(headerPath).get(key).unwrapped().asInstanceOf[String])
        }.toMap
      } else {
        Map.empty
      },

      jmsReply = cfg.getBoolean(jmsReplyPath, true),

      contentTypes = if (cfg.hasPath(cTypePath)) {
        Some(cfg.getStringList(cTypePath).asScala.toList)
      } else {
        None
      },

      isSoap = cfg.getBoolean(isSoapPath, false),

      encoding = cfg.getString(encodingPath, "UTF-8")
    )
  }
}

case class JmsOperationConfig(
  // This is the destination used for the request / reply operation
  destination : String,

  // Timeout in Milliseconds for the operation
  timeout : Long,

  // These are the headers that will be sent via JMS for the operation
  header : Map[String, String],

  jmsReply : Boolean,

  contentTypes : Option[List[String]],

  isSoap : Boolean,

  encoding : String

)
