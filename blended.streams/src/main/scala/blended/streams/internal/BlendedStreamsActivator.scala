package blended.streams.internal

import java.io.File

import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import blended.util.logging.Logger
import blended.streams.transaction.FlowTransactionManagerConfig
import blended.streams.transaction.FlowTransactionManager
import blended.streams.transaction.internal.FileFlowTransactionManager

class BlendedStreamsActivator extends DominoActivator
  with TypesafeConfigWatching {

  private val log : Logger = Logger[BlendedStreamsActivator]  

  whenBundleActive {
    whenTypesafeConfigAvailable{ (cfg, idSvc) =>

      log.info(s"Starting bundle [${bundleContext.getBundle().getSymbolicName()}]")      

      val baseDir : File = new File(idSvc.getContainerContext().getContainerDirectory())
      val tMgrConfig : FlowTransactionManagerConfig = FlowTransactionManagerConfig.fromConfig(baseDir, cfg)
      val tMgr : FlowTransactionManager = new FileFlowTransactionManager(tMgrConfig)

      tMgr.providesService[FlowTransactionManager]("directory" -> tMgrConfig.dir.getAbsolutePath())
    }
  }
}
