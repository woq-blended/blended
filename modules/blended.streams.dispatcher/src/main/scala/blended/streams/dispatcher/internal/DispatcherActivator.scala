package blended.streams.dispatcher.internal

import akka.actor.ActorSystem
import blended.akka.ActorSystemWatching
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import blended.streams.dispatcher.internal.builder.{DispatcherBuilderSupport, RunnableDispatcher}
import blended.streams.jms._
import blended.streams.transaction.FlowTransactionManager
import blended.util.logging.Logger
import com.typesafe.config.Config
import domino.DominoActivator

class DispatcherActivator extends DominoActivator
  with ActorSystemWatching
  with JmsStreamSupport {

  private val log : Logger = Logger[DispatcherActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[BlendedStreamsConfig]{streamsCfg =>
        whenServicePresent[FlowTransactionManager] { tMgr =>
          whenServicePresent[BridgeProviderRegistry] { registry =>

            try {
              val internalProvider : BridgeProviderConfig = registry.internalProvider.get
              log.info(s"Initializing Dispatcher with internal connection factory [${internalProvider.id}]")

              whenAdvancedServicePresent[IdAwareConnectionFactory](internalProvider.osgiBrokerFilter) { cf =>

                val bs = new DispatcherBuilderSupport {
                  override def containerConfig: Config = cfg.ctContext.containerConfig
                }

                implicit val system: ActorSystem = cfg.system

                val routerCfg = ResourceTypeRouterConfig.create(
                  ctCtxt = cfg.ctContext,
                  provider = registry,
                  cfg = cfg.config
                ).get

                val dispatcher = new RunnableDispatcher(
                  registry = registry,
                  cf = cf,
                  bs = bs,
                  ctCtxt = cfg.ctContext,
                  tMgr = tMgr,
                  streamsCfg = streamsCfg,
                  routerCfg = routerCfg
                )

                dispatcher.start()

                onStop {
                  log.info("Stopping dispatcher flows.")
                  dispatcher.stop()
                }
              }
            } catch {
              case t: Throwable =>
                log.warn(t)("Failed to start dispatcher")
            }
          }
        }
      }
    }
  }
}
