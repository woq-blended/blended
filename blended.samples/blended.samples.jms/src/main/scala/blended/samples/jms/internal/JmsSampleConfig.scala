package blended.samples.jms.internal

import com.typesafe.config.Config

object JmsSampleConfig {
  def apply(cfg : Config) : JmsSampleConfig = new JmsSampleConfig(
    destination = if (cfg.hasPath("destination")) {
      cfg.getString("destination")
    } else {
      "topic:jmsSample"
    },

    producerInterval = if (cfg.hasPath("producerInterval")) {
      cfg.getLong("producerInterval")
    } else {
      0L
    },

    consumeSelector = if (cfg.hasPath("consumeSelector")) {
      Some(cfg.getString("consumeSelector"))
    } else {
      None
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
