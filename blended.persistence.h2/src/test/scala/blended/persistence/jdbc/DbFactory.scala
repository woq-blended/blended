package blended.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import java.io.File

import javax.sql.DataSource
import blended.util.logging.Logger
import org.springframework.transaction.PlatformTransactionManager
import blended.testsupport.TestFile

/**
 * A H2 Database factory to testing.
 */
trait DbFactory extends TestFile {

  private[this] val log = Logger[DbFactory.type]

  def createDataSource(dir: File, name: String): HikariDataSource = {
    new File(dir, name).mkdirs()
    val url = "jdbc:h2:" + dir.getAbsolutePath() + "/" + name
    log.debug(s"Using database url: ${url}")
    val dataSource = new HikariDataSource()
    dataSource.setJdbcUrl(url)
    dataSource.setUsername("admin")
    dataSource.setPassword("admin")
    dataSource
  }

  def withDataSource[T](dir: File, name: String)(f: DataSource => T): T = {
    val dataSource = createDataSource(dir, "db")
    try {
      f(dataSource)
    } finally {
      dataSource.close()
    }
  }

  case class WithTestPersistenceServiceContext(persistenceService: PersistenceServiceJdbc, txManager: PlatformTransactionManager)

  /**
   * Use this to run a test against a pre-initialized Persistence Service. The schema is created, but no data is present.
   */
  def withTestPersistenceService(
    dir: Option[File] = None,
    deletePolicy: TestFile.DeletePolicy = TestFile.DeleteWhenNoFailure
  )(
    f: WithTestPersistenceServiceContext => Unit
  ): Unit = {

    def worker(dir: File): Unit = {
      DbFactory.withDataSource(dir, "db") { dataSource =>
        val dao = new PersistedClassDao(dataSource)
        val txMgr = new DummyPlatformTransactionManager()
        dao.init()
        val exp = new PersistenceServiceJdbc(txMgr, dao)
        f(WithTestPersistenceServiceContext(exp, txMgr))
      }
    }

    dir match {
      case Some(d) => worker(d)
      case None =>
        withTestDir(new File("target/tmp")) { dir =>
          worker(dir)
        }(deletePolicy)
    }
  }

}

object DbFactory extends DbFactory {
}
