package blended.activemq.brokerstarter

import akka.actor.{PoisonPill, Props}
import blended.activemq.brokerstarter.internal.{BrokerControlActor, StartBroker}
import blended.akka.ActorSystemWatching
import domino.DominoActivator

class BrokerActivator extends DominoActivator
  with ActorSystemWatching  {

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      val actor = osgiCfg.system.actorOf(Props[BrokerControlActor], bundleContext.getBundle().getSymbolicName())
      actor ! StartBroker(osgiCfg)

      onStop {
        actor ! PoisonPill
      }
    }
  }
}
