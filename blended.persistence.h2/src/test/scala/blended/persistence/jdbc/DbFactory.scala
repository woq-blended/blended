package blended.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import java.io.File
import javax.sql.DataSource

object DbFactory {

  def createDataSource(dir: File, name: String): HikariDataSource = {
    new File(dir, name).mkdirs()
    val url = "jdbc:h2:" + dir.getAbsolutePath() + "/" + name
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