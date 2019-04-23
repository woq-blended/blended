package blended.streams.internal

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.ActorSystemWatching
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.jms.{JmsRetryConfig, JmsRetryProcessor}
import blended.util.logging.Logger
import com.typesafe.config.Config
import domino.DominoActivator

import scala.util.control.NonFatal

class StreamsActivator extends DominoActivator
  with ActorSystemWatching {

  private[this] val log : Logger = Logger[StreamsActivator]

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val cfg : Config = osgiCfg.config
      val idSvc : ContainerIdentifierService = osgiCfg.idSvc

      implicit val system : ActorSystem = osgiCfg.system
      implicit val materializer : Materializer = ActorMaterializer()

      if (cfg.hasPath("jms.retry")) {
        log.info("Initialising JMS Retry processor ...")

        try {
          val rawConfig : Config = cfg.getConfig("jms.retry")

          val vendor : String = rawConfig.getString("vendor")
          val provider : String = rawConfig.getString("provider")

          whenAdvancedServicePresent[IdAwareConnectionFactory](s"(&(vendor=$vendor)(provider=$provider))") { cf =>
            val retryCfg : JmsRetryConfig = JmsRetryConfig.fromConfig(idSvc, cf, rawConfig).get
            val processor = new JmsRetryProcessor(s"$vendor:$provider", retryCfg)

            processor.start()

            onStop {
              processor.stop()
            }
          }

        } catch {
          case NonFatal(t) =>
            log.error(t)("Error initialising Jms Retry Processor")
            throw t
        }
      } else {
        log.info("No configuration for retry processor found...Jms Retry will be unavailable")
      }
    }
  }
}
