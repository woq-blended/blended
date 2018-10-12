package blended.jms.bridge.internal

import akka.actor.{OneForOneStrategy, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffSupervisor}
import blended.akka.ActorSystemWatching
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.jms.JmsSettings
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherContext
import domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import blended.util.config.Implicits._

class BridgeActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[BridgeActivator]

  private[this] def getProperty(context: ServiceWatcherContext[IdAwareConnectionFactory], name : String) : Option[String] = {
    Option(context.ref.getProperty(name)) match {
      case None => None
      case Some(o) => Some(o.toString())
    }
  }
  private[this] def identifyCf(context: ServiceWatcherContext[IdAwareConnectionFactory]) : Try[(String, String)] = Try {
    (getProperty(context, "vendor"), getProperty(context, "provider")) match {
      case (Some(v), Some(p)) => (v, p)
      case (v, p) =>
        val msg = s"Detected connection Factory [$v, $p] is missing either the vendor or provider property."
        throw new Exception(msg)
    }
  }

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val headerPrefix = osgiCfg.idSvc.containerContext.getContainerConfig().getString("blended.flow.headerPrefix", JmsSettings.defaultHeaderPrefix)

      val providerList = osgiCfg.config.getConfigList("provider").asScala.map { p =>
          BridgeProviderConfig.create(osgiCfg.idSvc, p).get
        }.toList

      val inboundList : List[InboundConfig ]=
        osgiCfg.config.getConfigList("inbound").asScala.map { i =>
          InboundConfig.create(osgiCfg.idSvc, i).get
        }.toList

      val queuePrefix = osgiCfg.config.getString("queuePrefix", "blended.bridge")

      val (internalVendor, internalProvider) = providerList.filter(_.internal) match {
        case Nil => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
        case h :: Nil => (h.vendor, h.provider.getOrElse(h.vendor))
        case h :: _ => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
      }

      log.info(s"Bridge Activator is using [$internalVendor:$internalProvider] as internal JMS Provider.")

      whenAdvancedServicePresent[IdAwareConnectionFactory](s"(&(vendor=${internalVendor})(provider=${internalProvider}))") { cf =>

        try {

          val bridgeProps = BridgeController.props(
            BridgeControllerConfig(
              internalVendor = internalVendor,
              internalProvider = Some(internalProvider),
              queuePrefix = queuePrefix,
              headerPrefix = headerPrefix,
              internalConnectionFactory = cf,
              jmsProvider = providerList,
              inbound = inboundList
            )
          )

          val restartProps = BackoffSupervisor.props(
            Backoff.onStop(
              bridgeProps,
              childName = "BridgeController",
              minBackoff = 3.seconds,
              maxBackoff = 1.minute,
              randomFactor = 0.2,
              maxNrOfRetries = -1
            ).withAutoReset(30.seconds)
              .withSupervisorStrategy(
                OneForOneStrategy() {
                  case _ => SupervisorStrategy.Restart
                }
              )
          )

          log.info("Starting JMS bridge supervising actor.")
          val bridge = osgiCfg.system.actorOf(bridgeProps, "BridgeSupervisor")

          watchServices[IdAwareConnectionFactory] {

            case AddingService(cf, context) => identifyCf(context) match {
              case Success(_) =>
                bridge ! BridgeController.AddConnectionFactory(cf)
              case Failure(t) =>
                log.warn(t.getMessage)
            }

            case ModifiedService(cf, context) => identifyCf(context) match {
              case Success(_) =>
                bridge ! BridgeController.RemoveConnectionFactory(cf)
                bridge ! BridgeController.AddConnectionFactory(cf)
              case Failure(t) =>
                log.warn(t.getMessage)
            }

            case RemovedService(cf, context) => identifyCf(context) match {
              case Success(_) =>
                bridge ! BridgeController.RemoveConnectionFactory(cf)
              case Failure(t) =>
                log.warn(t.getMessage)
            }
          }

          onStop {
            log.info("Stopping JMS bridge supervising actor.")
            osgiCfg.system.stop(bridge)
          }

        } catch {
          case t : Throwable =>
            log.error(t)("Error starting JMS bridge")
        }
      }
    }
  }
}
