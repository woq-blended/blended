package blended.streams.internal

import java.io.File

import blended.akka.ActorSystemWatching
import blended.streams.transaction.{FlowTransactionManager, FlowTransactionManagerConfig, TransactionManagerCleanupActor}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator

class BlendedStreamsActivator extends DominoActivator
  with ActorSystemWatching {

  private val log : Logger = Logger[BlendedStreamsActivator]

  whenBundleActive {
    whenActorSystemAvailable{ osgiCfg =>

      log.info(s"Starting bundle [${bundleContext.getBundle().getSymbolicName()}]")

      val baseDir : File = new File(osgiCfg.idSvc.getContainerContext().getContainerDirectory())

      val tMgrConfig : FlowTransactionManagerConfig =
        FlowTransactionManagerConfig.fromConfig(baseDir, osgiCfg.config.getConfigOption("transaction"))

      val tMgr : FlowTransactionManager = new FileFlowTransactionManager(tMgrConfig)

      log.info(s"Starting clean up actor for transaction manager")
      osgiCfg.system.actorOf(TransactionManagerCleanupActor.props(tMgr, tMgrConfig))

      tMgr.providesService[FlowTransactionManager]("directory" -> tMgrConfig.dir.getAbsolutePath())
    }
  }
}
