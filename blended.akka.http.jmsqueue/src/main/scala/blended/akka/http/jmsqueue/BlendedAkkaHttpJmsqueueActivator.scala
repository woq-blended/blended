package blended.akka.http.jmsqueue

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.akka.http.jmsqueue.internal.{HttpQueueConfig, OsgiHttpQueueService}
import domino.DominoActivator

class BlendedAkkaHttpJmsqueueActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      val qCfg = HttpQueueConfig.fromConfig(cfg.config)
      val service = new OsgiHttpQueueService(qConfig = qCfg, bundleContext = cfg.bundleContext)
      val context = cfg.config.getString("webcontext")
      SimpleHttpContext(prefix = context, route = service.httpRoute).providesService[HttpContext]
    }
  }

}
