package blended.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import java.io.File
import javax.sql.DataSource

object DbFactory {

  val log = org.log4s.getLogger
  
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

}