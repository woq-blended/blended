package blended.persistence.h2.internal

import java.io.File

import scala.util.Failure
import scala.util.Success

import blended.domino.TypesafeConfigWatching
import blended.persistence.PersistenceService
import blended.persistence.jdbc.PersistedClassDao
import blended.persistence.jdbc.PersistenceServiceJdbc
import blended.util.logging.Logger
import com.zaxxer.hikari.HikariDataSource
import domino.DominoActivator
import org.h2.jdbcx.JdbcDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager

class H2Activator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[H2Activator]

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
            val f = new File(dbPath).getParentFile()
            if (f != null && !f.exists()) {
              log.debug("dbPath is defined but does not exists. About to create it")
              f.mkdirs()
            }
          }

          val ds = new JdbcDataSource();
          ds.setURL(dbUrl);
          ds.setUser("admin");
          ds.setPassword("admin");

          // to avoid OSGi classloading issue with Hikari datasource, we feed a DS from H2 into Hikari
          val dataSource = new HikariDataSource()
          dataSource.setDataSource(ds)
          //          dataSource.setJdbcUrl(dbUrl)
          //          dataSource.setUsername("admin")
          //          dataSource.setPassword("admin")
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
