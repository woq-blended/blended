package blended.streams.dispatcher.internal

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.ActorSystemWatching
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.dispatcher.internal.builder.{DispatcherBuilderSupport, RunnableDispatcher}
import blended.streams.jms._
import blended.util.logging.Logger
import com.typesafe.config.Config
import domino.DominoActivator

class DispatcherActivator extends DominoActivator
  with ActorSystemWatching
  with JmsStreamSupport {

  private val log : Logger = Logger[DispatcherActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[BridgeProviderRegistry] { registry =>

        try {
          val internalProvider = registry.internalProvider.get
          log.info(s"Initializing Dispatcher with internal connection factory [${internalProvider.id}]")

          val bs = new DispatcherBuilderSupport {
            override def containerConfig: Config = cfg.idSvc.containerContext.getContainerConfig()
            override val streamLogger: Logger = Logger("flow.dispatcher")
          }

          whenAdvancedServicePresent[IdAwareConnectionFactory](internalProvider.osgiBrokerFilter) { cf =>

            implicit val system: ActorSystem = cfg.system
            implicit val materializer: Materializer = ActorMaterializer()

            val routerCfg = ResourceTypeRouterConfig.create(
              idSvc = cfg.idSvc,
              provider = registry,
              cfg = cfg.config
            ).get

            val dispatcher = new RunnableDispatcher(
              registry = registry,
              cf = cf,
              bs = bs,
              idSvc = cfg.idSvc,
              routerCfg = routerCfg
            )

            dispatcher.start()

            onStop {
              log.info("Stopping dispatcher flows.")
              dispatcher.stop()
            }
          }
        } catch {
          case t : Throwable =>
            log.warn(t)("Failed to start dispatcher")
        }
      }
    }
  }
}
