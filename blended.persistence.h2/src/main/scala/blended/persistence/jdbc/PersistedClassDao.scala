package blended.persistence.jdbc

import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import javax.sql.DataSource
import liquibase.database.jvm.JdbcConnection
import liquibase.database.DatabaseFactory
import liquibase.database.Database
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.Liquibase
import scala.util.Try
import scala.collection.{ immutable => sci }

class PersistedClassDao(dataSource: DataSource) {

  private[this] val log = org.log4s.getLogger

  private[this] val jdbcTemplate = new NamedParameterJdbcTemplate(dataSource)

  object PC {
    val Table = "PersistedClass"

    val Id: String = "id"
    val Name: String = "name"
  }

  object PF {
    val Table = "PersistedField"

    val HolderId = "holderId"
    val FieldId = "fieldId"
    val BaseFieldId = "baseFieldId"
    val Name = "name"
    val ValueLong = "valueLong"
    val ValueDouble = "valueDouble"
    val ValueString = "valueString"
    val TypeName = "typeName"
  }

  def persist(persistedClass: PersistedClass): PersistedClass = {

    // persist the holder class
    val persistedClassId = {
      val pcCols = Seq(PC.Name)
      val sql = s"insert into ${PC.Table} (${pcCols.mkString(",")}) values (${pcCols.map(":" + _).mkString(",")})"

      val paramSource = new MapSqlParameterSource()
      paramSource.addValue(PC.Name, persistedClass.name)
      val keyHolder = new GeneratedKeyHolder()

      val rows = jdbcTemplate.update(sql, paramSource, keyHolder)
      val id = keyHolder.getKeys().get(PC.Id).asInstanceOf[Long]
      id
    }

    // persist the fields
    {
      val pfCols = Seq(
        PF.HolderId, PF.FieldId, PF.BaseFieldId,
        PF.Name, PF.ValueLong, PF.ValueDouble, PF.ValueString,
        PF.TypeName
      )
      val sql = s"insert into ${PF.Table} (${pfCols.mkString(",")}) values (${pfCols.map(":" + _).mkString(",")})"

      // we work in-memory, so we expect no performance gain from batch operaton for now
      persistedClass.fields.foreach { field =>
        val paramSource = new MapSqlParameterSource()
        paramSource.addValue(PF.HolderId, persistedClassId)
        paramSource.addValue(PF.FieldId, field.fieldId)
        paramSource.addValue(PF.BaseFieldId, field.baseFieldId.orNull)
        paramSource.addValue(PF.Name, field.name)
        paramSource.addValue(PF.ValueLong, field.valueLong.orNull)
        paramSource.addValue(PF.ValueDouble, field.valueDouble.orNull)
        paramSource.addValue(PF.ValueString, field.valueString.orNull)
        paramSource.addValue(PF.TypeName, field.typeName.name)
        val rows = jdbcTemplate.update(sql, paramSource)
      }
    }

    persistedClass.copy(id = Some(persistedClassId))
  }

  def delete(pClass: String, id: Long): Unit = {
    val sql = s"delete from ${PC.Table} where ${PC.Id} = :id and ${PC.Name} = :name"
    val paramSource = new MapSqlParameterSource()
    paramSource.addValue(PC.Id, id)
    paramSource.addValue(PC.Name, pClass)
    jdbcTemplate.update(sql, paramSource)
  }

  /**
   * Initialize Database
   * @throws SQLException
   * @throws LiquibaseException
   */
  def init(): Try[Unit] = Try {
    val changelogName = "blended/persistence/jdbc/PersistedClassDao-changelog.xml"
    log.debug("Loading database changelog from: " + changelogName)
    val jdbcConnection = new JdbcConnection(dataSource.getConnection())
    try {
      val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection)
      val liquibase = new Liquibase(changelogName, new ClassLoaderResourceAccessor(), database)
      liquibase.update("")
      log.debug("Database changelog applied")
    } finally {
      jdbcConnection.close()
    }
  }

  def findAll(pClass: String): sci.Seq[PersistedClass] = {
    ???
  }

}
