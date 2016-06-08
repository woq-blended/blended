package blended.persistence.orient.internal

import org.slf4j.LoggerFactory

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import blended.persistence.orient.PersistenceService
import domino.DominoActivator

class OrientActivator() extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[OrientActivator])

  whenBundleActive {
    log.debug("About to start {}", classOf[OrientActivator])

    val dbUrl = "plocal:tmp$blended"
    val dbUserName = "admin"
    val dbPassword = "admin"
    val dbPool = new OPartitionedDatabasePool(dbUrl, dbUserName, dbPassword)

    val db = dbPool.acquire()
    try {
      if (!db.exists()) {
        db.create()
      }
    } finally {
      db.close()
    }

    val orientExperimental = new PersistenceServiceOrientDb(dbPool)
    orientExperimental.providesService[PersistenceService]

    onStop {
      dbPool.close()
    }

  }

}
