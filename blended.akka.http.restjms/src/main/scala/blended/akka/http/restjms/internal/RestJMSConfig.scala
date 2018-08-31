package blended.akka.http.restjms.internal

import com.typesafe.config.Config

import scala.collection.JavaConverters._

object RestJMSConfig {

  val operationsPath = "operations"

  def fromConfig(cfg: Config) : RestJMSConfig = {

    val operations : Map[String, JmsOperationConfig] = cfg.hasPath(operationsPath) match {
      case false => Map.empty
      case true =>
        cfg.getObject(operationsPath).keySet().asScala.map { key =>
          (key, JmsOperationConfig(cfg.getConfig(operationsPath).getConfig(s""""$key"""")))
        }.toMap
    }

    RestJMSConfig(operations)
  }
}

case class RestJMSConfig(
  // These are the configured operations
  operations : Map[String, JmsOperationConfig]
)

object JmsOperationConfig {
  def apply(cfg: Config) : JmsOperationConfig = {

    val destinationPath = "destination"
    val headerPath = "header"
    val receivetimeoutPath = "receivetimeout"
    val timeoutPath = "timeout"
    val jmsReplyPath = "jmsreply"
    val cTypePath = "contentTypes"
    val isSoapPath = "isSoap"
    val encodingPath = "encoding"

    new JmsOperationConfig (
      destination = cfg.getString(destinationPath),

      timeout = cfg.hasPath(timeoutPath) match {
        case true => cfg.getLong(timeoutPath)
        case false => 1000L
      },

      receivetimeout = cfg.hasPath(receivetimeoutPath) match {
        case true => cfg.getLong(receivetimeoutPath)
        case false => 250L
      },

      header = cfg.hasPath(headerPath) match {
        case false => Map.empty
        case true => cfg.getObject(headerPath).keySet().asScala.map { key =>
          (key, cfg.getObject(headerPath).get(key).unwrapped().asInstanceOf[String])
        }.toMap
      },

      jmsReply = !cfg.hasPath(jmsReplyPath) || cfg.getBoolean(jmsReplyPath),

      contentTypes = cfg.hasPath(cTypePath) match {
        case false => None
        case true => Some(cfg.getStringList(cTypePath).asScala.toList)
      },

      isSoap = cfg.hasPath(isSoapPath) && cfg.getBoolean(isSoapPath),

      encoding = if (cfg.hasPath(encodingPath)) cfg.getString(encodingPath) else "UTF-8"
    )
  }
}

case class JmsOperationConfig(
  // This is the destination used for the request / reply operation
  destination : String,

  // The receive Timeout to configure the polling interval for the response consumer
  receivetimeout : Long,

  // Timeout in Milliseconds for the operation
  timeout: Long,

  // These are the headers that will be sent via JMS for the operation
  header : Map[String, String],

  jmsReply : Boolean,

  contentTypes : Option[List[String]],

  isSoap : Boolean,

  encoding : String

)
