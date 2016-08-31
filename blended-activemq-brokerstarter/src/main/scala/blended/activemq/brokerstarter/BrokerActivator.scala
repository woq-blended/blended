package blended.activemq.brokerstarter

import akka.actor.{ActorSystem, PoisonPill, Props}
import blended.activemq.brokerstarter.internal.{BrokerControlActor, StartBroker, StopBroker}
import blended.akka.OSGIActorConfig
import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.slf4j.LoggerFactory

class BrokerActivator extends DominoActivator
  with TypesafeConfigWatching {

  whenBundleActive {
    val log = LoggerFactory.getLogger(classOf[BrokerActivator])

    whenServicePresent[ActorSystem] { actorSys =>

      val actor = actorSys.actorOf(Props[BrokerControlActor], bundleContext.getBundle().getSymbolicName())

      onStop {
        actor ! PoisonPill
      }

      whenTypesafeConfigAvailable { (cfg, idSvc) =>
        actor ! StartBroker(OSGIActorConfig(bundleContext, actorSys, cfg, idSvc))

        onStop {
          actor ! StopBroker
        }
      }
    }
  }
}
