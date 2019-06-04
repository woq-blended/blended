package blended.samples.jms.internal

import com.typesafe.config.Config

object JmsSampleConfig {
  def apply(cfg : Config) : JmsSampleConfig = new JmsSampleConfig(
    destination = cfg.hasPath("destination") match {
      case true  => cfg.getString("destination")
      case false => "topic:jmsSample"
    },

    producerInterval = cfg.hasPath("producerInterval") match {
      case true  => cfg.getLong("producerInterval")
      case false => 0l
    },

    consumeSelector = cfg.hasPath("consumeSelector") match {
      case true  => Some(cfg.getString("consumeSelector"))
      case false => None
    }
  )
}

case class JmsSampleConfig(
  // The destination we use for the demo
  destination : String,

  // 0 == producer disabled
  producerInterval : Long,

  // consumer msg selector
  consumeSelector : Option[String]

)
