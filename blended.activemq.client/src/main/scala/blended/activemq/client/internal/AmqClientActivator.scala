package blended.activemq.client.internal

import akka.actor.ActorSystem
import blended.activemq.client.{ConnectionVerifier, VerificationFailedHandler}
import blended.akka.ActorSystemWatching
import blended.jms.utils._
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config
import domino.DominoActivator
import domino.logging.Logging
import javax.jms.ConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AmqClientActivator extends DominoActivator with ActorSystemWatching with Logging {

  private[this] val log = Logger[AmqClientActivator]

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      implicit val eCtxt : ExecutionContext = osgiCfg.system.dispatcher

      // First we register a default verifier
      new DefaultConnectionVerifier().providesService[ConnectionVerifier]("name" -> "default")
      new DefaultVerificationFailedHandler(osgiCfg.bundleContext).providesService[VerificationFailedHandler]("name" -> "default")

      val verifierName = osgiCfg.config.getString("verifier")
      val failedHandlerName = osgiCfg.config.getString("failedHandler")
      log.info(s"ActiveMQ Client connections using verifier [$verifierName]")
      log.info(s"Using verification failed handler [$failedHandlerName]")

      whenAdvancedServicePresent[ConnectionVerifier](s"(name=$verifierName)") { verifier =>
        whenAdvancedServicePresent[VerificationFailedHandler](s"(name=$failedHandlerName)") { failedHandler =>

          val cfg: Config = osgiCfg.config
          implicit val system: ActorSystem = osgiCfg.system

          // TODO: Include connection verifier
          val cfgMap: Map[String, Config] = cfg.getConfigMap("connections", Map.empty)
          log.info(s"Starting ActiveMQ client connection(s) : [${cfgMap.values.mkString(",")}]")

          cfgMap.foreach { case (key, config) =>
            val connectionCfg: ConnectionConfig = BlendedJMSConnectionConfig.fromConfig(
              stringResolver = osgiCfg.idSvc.resolvePropertyString
            )(
              vendor = "activemq",
              provider = key,
              cfg = config
            ).copy(
              cfClassName = Some(classOf[ActiveMQConnectionFactory].getName())
            )

            val cf: IdAwareConnectionFactory = new BlendedSingleConnectionFactory(
              connectionCfg, Some(osgiCfg.bundleContext)
            )

            verifier.verifyConnection(cf).onComplete {
              case Success(b) => if (b) {
                  log.info(s"Connection [${cf.vendor}:${cf.provider}] verified and ready to use.")
                  cf.providesService[ConnectionFactory, IdAwareConnectionFactory](
                    "vendor" -> connectionCfg.vendor,
                    "provider" -> connectionCfg.provider
                  )
                } else {
                  log.warn(s"Failed to verify connection [${cf.vendor}:${cf.provider}]...invoking failed handler")
                  failedHandler.verificationFailed(cf)
                }
              case Failure(t) =>
                s"Unable to verify connection [${cf.vendor}:${cf.provider}]. This connection will not be active"
            }
          }
        }
      }
    }
  }
}
