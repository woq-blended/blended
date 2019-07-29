package blended.streams.internal

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import blended.akka.ActorSystemWatching
import blended.jms.utils._
import blended.streams.BlendedStreamsConfig
import blended.streams.jms.internal.JmsKeepAliveActor
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.transaction.{FlowTransactionManager, FlowTransactionManagerConfig, TransactionManagerCleanupActor}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent

class BlendedStreamsActivator extends DominoActivator
  with ActorSystemWatching {

  private val log : Logger = Logger[BlendedStreamsActivator]

  whenBundleActive {
    whenActorSystemAvailable{ osgiCfg =>

      log.info(s"Starting bundle [${bundleContext.getBundle().getSymbolicName()}]")
      log.debug(s"${osgiCfg.config}")

      implicit val system : ActorSystem = osgiCfg.system

      val baseDir : File = new File(osgiCfg.idSvc.getContainerContext().getContainerDirectory())

      // initialise the flow transaction for persisting flow transaction states
      val tMgrConfig : FlowTransactionManagerConfig =
        FlowTransactionManagerConfig.fromConfig(baseDir, osgiCfg.config.getConfigOption("transaction"))

      val tMgr : FlowTransactionManager = new FileFlowTransactionManager(tMgrConfig)

      log.info(s"Starting clean up actor for transaction manager with config [$tMgrConfig]")
      val tMgrCleanup : ActorRef = osgiCfg.system.actorOf(TransactionManagerCleanupActor.props(tMgr, tMgrConfig))

      tMgr.providesService[FlowTransactionManager]("directory" -> tMgrConfig.dir.getAbsolutePath())

      new BlendedStreamsConfigImpl(osgiCfg.idSvc, osgiCfg.config).providesService[BlendedStreamsConfig]

      // initialise the JMS keep alive streams

      val jmsKeepAliveActor = osgiCfg.system.actorOf(Props[JmsKeepAliveActor])

      // We will watch for published instances of JMS connection configurations
      watchServices[BlendedJMSConnectionConfig]{
        case ServiceWatcherEvent.AddingService(cfCfg, _)   =>
          jmsKeepAliveActor ! AddedConnectionFactory(cfCfg)

        case ServiceWatcherEvent.ModifiedService(cfCfg, _) =>
          jmsKeepAliveActor ! RemovedConnectionFactory(cfCfg)
          jmsKeepAliveActor ! AddedConnectionFactory(cfCfg)

        case ServiceWatcherEvent.RemovedService(cfCfg, _)  =>
          jmsKeepAliveActor ! RemovedConnectionFactory(cfCfg)
      }

      onStop{
        osgiCfg.system.stop(tMgrCleanup)
        osgiCfg.system.stop(jmsKeepAliveActor)
      }
    }
  }
}
