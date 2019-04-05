package blended.persistence.h2.internal

import java.io.File

import blended.domino.TypesafeConfigWatching
import blended.persistence.PersistenceService
import blended.persistence.jdbc.{PersistedClassDao, PersistenceServiceJdbc}
import blended.util.logging.Logger
import com.zaxxer.hikari.HikariDataSource
import domino.DominoActivator
import org.h2.jdbcx.JdbcDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager

import scala.util.{Failure, Success}

class H2Activator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[H2Activator]

  whenBundleActive {
    log.debug("About to start " + getClass())

    whenTypesafeConfigAvailable { (config, idService) =>

      def getString(path: String): Option[String] = {
        val lookup :Option[String] = if (config.hasPath(path)) Some(config.getString(path)) else None
        lookup.map(v => idService.resolvePropertyString(v).map(_.toString()).get)
      }

      val dbPath : Option[String] = getString("dbPath")
      val dbUrl : Option[String] = getString("dbUrl").orElse(dbPath.map(p => s"jdbc:h2:${p}"))

      val dbUserName : Option[String] = getString("dbUserName")
      val dbPassword : Option[String] = getString("dbPassword")
      val extraOptions : Option[String] = getString("options")

      (dbUrl, dbUserName, dbPassword) match {
        case (None, _, _) =>
          sys.error("No 'dbUrl' defined in configuration. Cannot start H2 persistence service")

        case (_, None, _) =>
          sys.error("No 'dbUserName' defined in configuration. Cannot start H2 persistence service")

        case (_, _, None) =>
          sys.error("No 'dbPassword' defined in configuration. Cannot start H2 persistence service")

        case (Some(url), Some(user), Some(pwd)) =>
          dbPath.foreach { dbPath =>
            val f = new File(dbPath).getParentFile()
            if (f != null && !f.exists()) {
              log.debug("dbPath is defined but does not exists. About to create it")
              f.mkdirs()
            }
          }

          val finalUrl = url + extraOptions.filter(!_.isEmpty()).map(";" + _).getOrElse("")
          log.debug(s"DB url: [${finalUrl}]")
          
          val ds = new JdbcDataSource()
          ds.setURL(finalUrl)
          ds.setUser(user)
          ds.setPassword(pwd)

          // to avoid OSGi classloading issue with Hikari datasource, we feed a DS from H2 into Hikari
          val dataSource = new HikariDataSource()
          dataSource.setDataSource(ds)

          onStop {
            dataSource.close()
          }

          val txManager = new DataSourceTransactionManager(dataSource)

          val persistedClassDao = new PersistedClassDao(dataSource)
          persistedClassDao.init() match {
            case Success(()) =>
              log.info("Database initialized successfully")
              val persistenceService = new PersistenceServiceJdbc(txManager, persistedClassDao)
              persistenceService.providesService[PersistenceService]("dbUrl" -> url)

            case Failure(e) => log.error(e)("Could not initialize database")
          }

      }
    }

  }

}
