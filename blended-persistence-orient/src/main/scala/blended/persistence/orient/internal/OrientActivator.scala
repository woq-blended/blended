package blended.persistence.orient.internal

import org.slf4j.LoggerFactory

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import blended.persistence.orient.PersistenceService
import domino.DominoActivator
import java.io.File
import blended.domino.TypesafeConfigWatching
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class OrientActivator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[OrientActivator])

  whenBundleActive {
    log.debug("About to start {}", getClass())

    whenTypesafeConfigAvailable { (config, idService) =>

      def getString(path: String): Option[String] = if (config.hasPath(path)) Some(config.getString(path)) else None

      val dbPath = getString("dbPath")
      val dbUrl = getString("dbUrl").orElse(dbPath.map(p => s"plocal:${p}"))
      val dbUserName = getString("dbUserName")
      val dbPassword = getString("dbPassword")

      (dbUrl, dbUserName, dbPassword) match {
        case (None, _, _) =>
          sys.error("No 'dbUrl' defined in configuration. Cannot start persistence service")

        case (_, None, _) =>
          sys.error("No 'dbUserName' defined in configuration. Cannot start persistence service")

        case (_, _, None) =>
          sys.error("No 'dbPassword' defined in configuration. Cannot start persistence service")

        case (Some(dbUrl), Some(dbUserName), Some(dbPassword)) =>
          dbPath.foreach { dbPath =>
            val f = new File(dbPath)
            if (!f.exists()) {
              log.debug("dbPath is defined but does not exists. About to create it")
              new File(dbPath).mkdirs()
            }
          }

          {
            val db = new ODatabaseDocumentTx(dbUrl)
            try {
              if (!db.exists()) {
                log.debug("Database does not exist. About to create it at URL: {}", dbUrl)
                db.create()
              }
            } finally {
              db.close()
            }
          }

          val dbPool = new OPartitionedDatabasePool(dbUrl, dbUserName, dbPassword)

          onStop {
            log.debug("About to close database pool")
            dbPool.close()
          }

          val orientExperimental = new PersistenceServiceOrientDb(dbPool)
          orientExperimental.providesService[PersistenceService]

          log.debug("Started {}", getClass())
      }

    }

  }

}
