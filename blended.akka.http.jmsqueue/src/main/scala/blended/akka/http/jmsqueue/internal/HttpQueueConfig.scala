package blended.akka.http.jmsqueue.internal

import com.typesafe.config.Config

import scala.collection.JavaConverters._
import blended.util.config.Implicits._

object HttpQueueConfig {

  val queuesPath = "queues"

  def fromConfig(cfg : Config) : HttpQueueConfig = {
    val queues : Map[(String, String), ProviderQueueConfig] = cfg.hasPath(queuesPath) match {
      case false => Map.empty
      case true =>
        val provider : Seq[(String, String)] =
          for (
            v <- cfg.getObject(queuesPath).keySet().asScala.toSeq;
            p <- cfg.getConfig(queuesPath).getObject(v).keySet().asScala.toSeq
          ) yield (v, p)

        provider.map {
          case (v, p) =>
            val providerCfg = cfg.getConfig(queuesPath).getConfig(v).getConfig(p)

            val path = providerCfg.getString("path", p)
            val queueNames = providerCfg.getStringList("queues", List.empty)
            (v, p) -> ProviderQueueConfig(path, queueNames)
        }.toMap
    }

    HttpQueueConfig(queues)
  }
}

case class ProviderQueueConfig(path : String, queueNames : List[String])

case class HttpQueueConfig(
  httpQueues : Map[(String, String), ProviderQueueConfig]
)

