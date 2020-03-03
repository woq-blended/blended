package blended.jms.bridge.internal

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.ActorSystemWatching
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.BlendedStreamsConfig
import blended.streams.jms.{JmsRetryConfig, JmsRetryProcessor}
import blended.util.RichTry._
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherContext
import domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class BridgeActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[BridgeActivator]

  private[this] def getProperty(context : ServiceWatcherContext[IdAwareConnectionFactory], name : String) : Option[String] = {
    Option(context.ref.getProperty(name)) match {
      case None    => None
      case Some(o) => Some(o.toString())
    }
  }
  private[this] def identifyCf(context : ServiceWatcherContext[IdAwareConnectionFactory]) : Try[(String, String)] = Try {
    (getProperty(context, "vendor"), getProperty(context, "provider")) match {
      case (Some(v), Some(p)) => (v, p)
      case (v, p) =>
        val msg = s"Detected connection Factory [$v, $p] is missing either the vendor or provider property."
        throw new Exception(msg)
    }
  }

  // We maintain the streamBuilder factory as a function here, so that unit tests can override
  // the factory with stream builders throwing particular exceptions
  protected def streamBuilderFactory(system : ActorSystem)(materializer: Materializer)(
    bridgeCfg : BridgeStreamConfig, streamsCfg : BlendedStreamsConfig
  ) : BridgeStreamBuilder = new BridgeStreamBuilder(bridgeCfg, streamsCfg)(system, materializer)

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val providerList = osgiCfg.config.getConfigList("provider").asScala.map { p =>
        BridgeProviderConfig.create(osgiCfg.ctContext, p).unwrap
      }.toList

      log.info(s"Starting jms bridge with providers [${providerList.map(_.toString()).mkString(",")}]")

      val (internalVendor, internalProvider) = providerList.filter(_.internal) match {
        case Nil      => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
        case h :: Nil => (h.vendor, h.provider)
        case _ => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
      }

      val registry = new BridgeProviderRegistry(providerList)
      registry.providesService[BridgeProviderRegistry]

      log.info(s"Bridge Activator is using [$internalVendor:$internalProvider] as internal JMS Provider.")

      whenServicePresent[BlendedStreamsConfig]{ streamsCfg =>
        whenAdvancedServicePresent[IdAwareConnectionFactory](s"(&(vendor=$internalVendor)(provider=$internalProvider))") { cf =>

          val ctrlConfig = BridgeControllerConfig.create(
            cfg = osgiCfg.config,
            internalCf = cf,
            ctCtxt = osgiCfg.ctContext,
            streamsCfg = streamsCfg,
            streamBuilderFactory = streamBuilderFactory
          )

          ctrlConfig.registry.providesService[BridgeProviderRegistry]

          implicit val system: ActorSystem = osgiCfg.system
          implicit val materialzer: ActorMaterializer = ActorMaterializer()

          if (osgiCfg.config.hasPath("retry")) {

            registry.internalProvider.unwrap.retry.foreach { retryDest =>
              val retryCfg: JmsRetryConfig = JmsRetryConfig.fromConfig(
                ctCtxt = osgiCfg.ctContext,
                cf = cf,
                retryDestName = JmsDestination.asString(retryDest),
                retryFailedName = JmsDestination.asString(registry.internalProvider.unwrap.retryFailed),
                eventDestName = JmsDestination.asString(registry.internalProvider.unwrap.transactions),
                cfg = osgiCfg.config.getConfig("retry")
              ).get

              val processor = new JmsRetryProcessor(
                streamsCfg = streamsCfg,
                retryCfg = retryCfg
              )

              processor.start()

              onStop {
                processor.stop()
              }
            }
          }

          try {
            val bridgeProps = BridgeController.props(ctrlConfig)

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
            case NonFatal(t) =>
              log.error(t)("Error starting JMS bridge")
          }
        }
      }
    }
  }
}
