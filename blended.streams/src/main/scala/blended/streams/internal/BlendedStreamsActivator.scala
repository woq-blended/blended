package blended.streams.internal

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.ActorSystemWatching
import blended.jms.utils._
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig}
import blended.streams.jms.internal.{JmsKeepAliveController, KeepAliveProducerFactory, StreamKeepAliveProducerFactory}
import blended.streams.message.FlowEnvelopeLogger
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.transaction.{FlowTransactionManager, FlowTransactionManagerConfig, TransactionManagerCleanupActor}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent

class BlendedStreamsActivator extends DominoActivator
  with ActorSystemWatching {


  whenBundleActive {
    whenActorSystemAvailable{ osgiCfg =>

      val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(osgiCfg.ctContext)
      val log : Logger = Logger[BlendedStreamsActivator]

      log.debug(s"Starting bundle [${bundleContext.getBundle().getSymbolicName()}]")

      implicit val system : ActorSystem = osgiCfg.system
      implicit val materializer : Materializer = ActorMaterializer()


      val baseDir : File = new File(osgiCfg.ctContext.containerDirectory)

      // initialise the flow transaction for persisting flow transaction states
      val tMgrConfig : FlowTransactionManagerConfig =
        FlowTransactionManagerConfig.fromConfig(baseDir, osgiCfg.config.getConfigOption("transaction"))

      val tMgr : FlowTransactionManager = new FileFlowTransactionManager(tMgrConfig)

      log.debug(s"Starting clean up actor for transaction manager with config [$tMgrConfig]")
      val tMgrCleanup : ActorRef = osgiCfg.system.actorOf(TransactionManagerCleanupActor.props(tMgr, tMgrConfig))

      tMgr.providesService[FlowTransactionManager]("directory" -> tMgrConfig.dir.getAbsolutePath())

      val streamsCfg : BlendedStreamsConfig = BlendedStreamsConfig.create(osgiCfg.ctContext, osgiCfg.config)
      streamsCfg.providesService[BlendedStreamsConfig]

      // initialise the JMS keep alive streams

      val pf : KeepAliveProducerFactory = new StreamKeepAliveProducerFactory(
        log = bcf => FlowEnvelopeLogger.create(headerCfg, Logger(s"blended.streams.keepalive.${bcf.vendor}.${bcf.provider}")),
        ctCtxt = osgiCfg.ctContext,
        streamsCfg = streamsCfg
      )

      val jmsKeepAliveCtrl = osgiCfg.system.actorOf(Props(new JmsKeepAliveController(osgiCfg.ctContext, pf)))

      // We will watch for published instances of JMS connection configurations
      watchServices[IdAwareConnectionFactory]{
        case ServiceWatcherEvent.AddingService(cf, _)   => cf match {
          case bcf : BlendedSingleConnectionFactory => jmsKeepAliveCtrl ! AddedConnectionFactory(bcf)
          case _ =>
        }

        case ServiceWatcherEvent.ModifiedService(cf, _) => cf match {
          case bcf : BlendedSingleConnectionFactory =>
            jmsKeepAliveCtrl ! RemovedConnectionFactory(bcf)
            jmsKeepAliveCtrl ! AddedConnectionFactory(bcf)
          case _ =>
        }

        case ServiceWatcherEvent.RemovedService(cf, _)  => cf match {
          case bcf : BlendedSingleConnectionFactory => jmsKeepAliveCtrl ! RemovedConnectionFactory(bcf)
          case _ =>
        }
      }

      onStop{
        osgiCfg.system.stop(tMgrCleanup)
        osgiCfg.system.stop(jmsKeepAliveCtrl)
      }
    }
  }
}
