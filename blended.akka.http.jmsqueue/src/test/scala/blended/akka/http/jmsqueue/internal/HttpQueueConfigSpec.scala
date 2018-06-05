package blended.akka.http.jmsqueue.internal

import com.typesafe.config.ConfigFactory
import org.scalatest.FreeSpec

class HttpQueueConfigSpec extends FreeSpec {

  private[this] val log = org.log4s.getLogger

  "The HttpQueueConfig should" - {

    "read the config correctly" in {

      val cfg = ConfigFactory.load("httpqueue.conf").resolve()
      val qCfg = HttpQueueConfig.fromConfig(cfg)

      assert(qCfg.httpQueues.size == 3)

      assert(qCfg.httpQueues.keySet.contains("activemq" -> "activemq"))
      assert(qCfg.httpQueues.keySet.contains("sagum" -> "cc_queue"))
      assert(qCfg.httpQueues.keySet.contains("sagum" -> "central_queue"))

      assert(qCfg.httpQueues.get("activemq" -> "activemq").get.path == "activemq")
      assert(qCfg.httpQueues.get("activemq" -> "activemq").get.queueNames.size == 2)
      assert(qCfg.httpQueues.get("activemq" -> "activemq").get.queueNames.contains("Queue1"))
      assert(qCfg.httpQueues.get("activemq" -> "activemq").get.queueNames.contains("Queue2"))
    }
  }
}
