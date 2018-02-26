package blended.activemq.brokerstarter

import javax.net.ssl.SSLContext

import akka.actor.{PoisonPill, Props}
import blended.activemq.brokerstarter.internal.{BrokerControlActor, StartBroker}
import blended.akka.ActorSystemWatching
import domino.DominoActivator

class BrokerActivator extends DominoActivator
  with ActorSystemWatching  {

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      val withSsl = !osgiCfg.config.hasPath("withSsl") || osgiCfg.config.getBoolean("withSsl")

      if (withSsl) {
        whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>
          val actor = osgiCfg.system.actorOf(Props[BrokerControlActor], bundleContext.getBundle().getSymbolicName())
          actor ! StartBroker(osgiCfg, Some(sslCtxt))

          onStop {
            actor ! PoisonPill
          }
        }
      } else {
        val actor = osgiCfg.system.actorOf(Props[BrokerControlActor], bundleContext.getBundle().getSymbolicName())
        actor ! StartBroker(osgiCfg, None)

        onStop {
          actor ! PoisonPill
        }
      }

    }
  }
}
