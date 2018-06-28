package blended.persistence.h2.internal

import java.io.File

import scala.reflect.runtime.universe

import org.slf4j.LoggerFactory

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

import blended.domino.TypesafeConfigWatching
import blended.persistence.PersistenceService
import domino.DominoActivator
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import com.zaxxer.hikari.HikariDataSource
import blended.persistence.jdbc.PersistedClassDao
import blended.persistence.jdbc.PersistenceServiceJdbc
import scala.util.Success
import scala.util.Failure

class H2Activator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    log.debug("About to start " + getClass())

    whenTypesafeConfigAvailable { (config, idService) =>

      def getString(path: String): Option[String] = if (config.hasPath(path)) Some(config.getString(path)) else None

      val dbPath = getString("dbPath").map(dbPath => new File(dbPath).getAbsolutePath())
      val dbUrl = getString("dbUrl").orElse(dbPath.map(p => s"jdbc:h2:${p}"))
      val dbUserName = getString("dbUserName")
      val dbPassword = getString("dbPassword")

      (dbUrl, dbUserName, dbPassword) match {
        case (None, _, _) =>
          sys.error("No 'dbUrl' defined in configuration. Cannot start H2 persistence service")

        case (_, None, _) =>
          sys.error("No 'dbUserName' defined in configuration. Cannot start H2 persistence service")

        case (_, _, None) =>
          sys.error("No 'dbPassword' defined in configuration. Cannot start H2 persistence service")

        case (Some(dbUrl), Some(dbUserName), Some(dbPassword)) =>
          dbPath.foreach { dbPath =>
            val f = new File(dbPath)
            if (!f.exists()) {
              log.debug("dbPath is defined but does not exists. About to create it")
              new File(dbPath).mkdirs()
            }
          }

          val dataSource = new HikariDataSource()
          dataSource.setJdbcUrl(dbUrl)
          dataSource.setUsername("admin")
          dataSource.setPassword("admin")
          onStop {
            dataSource.close()
          }

          val txManager = new DataSourceTransactionManager(dataSource)

          val persistedClassDao = new PersistedClassDao(dataSource)
          persistedClassDao.init() match {
            case Success(()) =>
              log.info("Database initialized successfully")
              val persistenceService = new PersistenceServiceJdbc(txManager, persistedClassDao)
              persistenceService.providesService[PersistenceService]("dbUrl" -> dbUrl)

            case Failure(e) => log.error(e)("Could not initialize database")
          }

      }
    }

  }

}
