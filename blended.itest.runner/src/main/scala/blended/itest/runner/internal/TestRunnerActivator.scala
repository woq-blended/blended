package blended.itest.runner.internal

import domino.DominoActivator
import blended.util.logging.Logger
import blended.akka.ActorSystemWatching
import akka.actor.ActorRef
import blended.itest.runner.TestTemplateFactory
import domino.service_watching.ServiceWatcherEvent
import blended.itest.runner.Protocol
import blended.util.config.Implicits._
import blended.jmx.OpenMBeanExporter

class TestRunnerActivator extends DominoActivator with ActorSystemWatching {

  private val log : Logger = Logger[TestRunnerActivator]

  whenBundleActive {

    whenActorSystemAvailable{ cfg => 
      whenServicePresent[OpenMBeanExporter]{ exp => 

        val slots : Int = cfg.config.getInt("maxSlots", 5)
        log.info(s"Starting integration test manager with [$slots] slots")
        val testMgr : ActorRef = cfg.system.actorOf(TestManager.props(slots, Some(exp)))

        log.info(s"Starting to listen for test template factories ...")

        // Start listening for services 
        watchServices[TestTemplateFactory] {
          case ServiceWatcherEvent.AddingService(fact, watchContext)   => 
            testMgr ! Protocol.AddTestTemplateFactory(fact)
          case ServiceWatcherEvent.ModifiedService(fact, watchContext) => 
            testMgr ! Protocol.RemoveTestTemplateFactory(fact)
            testMgr ! Protocol.AddTestTemplateFactory(fact)
          case ServiceWatcherEvent.RemovedService(fact, watchContext)  =>
            testMgr ! Protocol.RemoveTestTemplateFactory(fact)
        }

        onStop{
          cfg.system.stop(testMgr)
        }
      }
    }
  }
  
}
