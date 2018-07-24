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
import org.springframework.jdbc.core.RowMapper
import scala.collection.JavaConverters._

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
    log.debug("About to persist class: ${pesistedClass}")

    // persist the holder class
    val persistedClassId = {
      val pcCols = Seq(PC.Name)
      val sql = s"insert into ${PC.Table} (${pcCols.mkString(",")}) values (${pcCols.map(":" + _).mkString(",")})"

      val paramSource = new MapSqlParameterSource()
      paramSource.addValue(PC.Name, persistedClass.name)
      val keyHolder = new GeneratedKeyHolder()

      val rows = jdbcTemplate.update(sql, paramSource, keyHolder)
      keyHolder.getKeys().get(PC.Id).asInstanceOf[java.lang.Long]
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
    // The schema defines a cascade delete, this we only need to delete the aggregate root
    val sql = s"delete from ${PC.Table} where ${PC.Id} = :id and ${PC.Name} = :name"
    val paramSource = new MapSqlParameterSource()
    paramSource.addValue(PC.Id, id)
    paramSource.addValue(PC.Name, pClass)
    jdbcTemplate.update(sql, paramSource)
  }

  /**
   * Initialize Database.
   *
   * @throws SQLException
   * @throws LiquibaseException
   */
  def init(): Try[Unit] = Try {
    val changelogName = "blended/persistence/jdbc/PersistedClassDao-changelog.xml"
    log.debug("Loading database changelog from: " + changelogName)
    val jdbcConnection = new JdbcConnection(dataSource.getConnection())
    try {
      val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection)
      val liquibase = new Liquibase(changelogName, new ClassLoaderResourceAccessor(getClass().getClassLoader()), database)
      liquibase.update("")
      log.debug(s"Database changelog applied: ${changelogName}")
    } catch {
      case e: Exception =>
        log.error(e)(s"Could not apply DB changelog: ${changelogName}")
        throw e
    } finally {
      jdbcConnection.close()
    }
  }

  def fieldRowMapper(prefix: String = ""): RowMapper[(Long, PersistedField)] = { (rs, nr) =>

    val holderId = rs.getLong(prefix + PF.HolderId)

    val fieldId = rs.getLong(prefix + PF.FieldId)
    val baseFieldId = Option(rs.getLong(prefix + PF.BaseFieldId)).filter(_ != 0)
    val name = rs.getString(prefix + PF.Name)
    val valueLong = Option(rs.getLong(prefix + PF.ValueLong))
    val valueDouble = Option(rs.getDouble(prefix + PF.ValueDouble))
    val valueString = Option(rs.getString(prefix + PF.ValueString))
    val typeName = TypeName.fromString(rs.getString(prefix + PF.TypeName)).get

    holderId -> PersistedField(fieldId, baseFieldId, name, valueLong, valueDouble, valueString, typeName)
  }

  def findAll(pClass: String): sci.Seq[PersistedClass] = {
    val pfCols = Seq(
      PF.HolderId, PF.FieldId, PF.BaseFieldId,
      PF.Name, PF.ValueLong, PF.ValueDouble, PF.ValueString,
      PF.TypeName
    )
    val sql = s"select ${pfCols.map("f." + _).mkString(", ")} from ${PF.Table} f join ${PC.Table} c on f.${PF.HolderId} = c.${PC.Id} where c.${PC.Name} = :className"
    val paramMap = new MapSqlParameterSource()
    paramMap.addValue("className", pClass)
    val rowMapper = fieldRowMapper()
    val allFields = jdbcTemplate.query(sql, paramMap, rowMapper)
    inferPersistedClassesFromFields(pClass, allFields.asScala)
  }

  def inferPersistedClassesFromFields(pClass: String, fields: Seq[(Long, PersistedField)]): sci.Seq[PersistedClass] = {
    val byId = fields.foldLeft(Map[Long, List[PersistedField]]()) { (map, rs) =>
      val id = rs._1
      val field = rs._2
      val tail = map.get(id).getOrElse(Nil)
      map + (id -> (field :: tail))
    }
    byId.toList.map { case (id, fields) => PersistedClass(id = Some(id), name = pClass, fields = fields.reverse) }
  }

  def createByExampleQuery(pClass: String, selectCols: Seq[String], fields: Seq[PersistedField]): (String, MapSqlParameterSource) = {
    val mainField = "field"
    val cls = "cls"

    var queryFields: List[String] = mainField :: Nil
    var queryCriterias: List[String] = Nil
    val queryParams = new MapSqlParameterSource()

    queryCriterias ::= s"${mainField}.${PF.HolderId} = ${cls}.${PC.Id} and ${cls}.${PC.Name} = :clsName"
    queryParams.addValue("clsName", pClass)

    fields.foreach { field =>
      val fName = s"f${field.fieldId}"
      queryFields ::= fName

      // match holder-id
      queryCriterias ::= s"${fName}.${PF.HolderId} = ${mainField}.${PF.HolderId}"

      // match name
      queryCriterias ::= s"${fName}.${PF.Name} = :${fName}Name"
      queryParams.addValue(fName + "Name", field.name)

      // match type name
      queryCriterias ::= s"${fName}.${PF.TypeName} = :${fName}TypeName"
      queryParams.addValue(fName + "TypeName", field.typeName.name)

      // match string value
      field.valueString match {
        case Some(v) =>
          queryCriterias ::= s"${fName}.${PF.ValueString} = :${fName}ValueString"
          queryParams.addValue(fName + "ValueString", v)

        case None =>
          queryCriterias ::= s"${fName}.${PF.ValueString} is null"
      }

      // match long value
      field.valueLong match {
        case Some(v) =>
          queryCriterias ::= s"${fName}.${PF.ValueLong} = :${fName}ValueLong"
          queryParams.addValue(fName + "ValueLong", v.longValue())

        case None =>
          queryCriterias ::= s"${fName}.${PF.ValueLong} is null"
      }

      // match double value
      field.valueDouble match {
        case Some(v) =>
          queryCriterias ::= s"${fName}.${PF.ValueDouble} = :${fName}ValueDouble"
          queryParams.addValue(fName + "ValueDouble", v.doubleValue())

        case None =>
          queryCriterias ::= s"${fName}.${PF.ValueDouble} is null"
      }

    }

    // Example
    // Field 1: PeristedField(fieldId = 1, name = k1, valueLong = None, valueDouble = None, valueString = "v1", typeName = "String"
    // Field 2: PeristedField(fieldId = 2, name = k2, valueLong = "2", valueDouble = None, valueString = None, typeName = "Long"
    // select field.id, field.typeName, ...
    // from PersistedField field, PersistedField field1, PersistedField field2, PersistedClass pclass
    // where field.holderId = pclass.id and pclass.name = :pclass
    //   and field.holderId = field1.holderId and field1.typeName = "String" and field1.name = "k1" and field1.valueString = "v1"
    //   and field.holderId = field2.holderId and field2.typeName = "Long" and field2.name = "k2" and field2.valueDouble = 2
    val sql = s"select ${selectCols.map(mainField + "." + _).mkString(", ")} " +
      s"\nfrom ${PC.Table} ${cls}, ${queryFields.reverse.map(PF.Table + " " + _).mkString(", ")} " +
      s"\nwhere ${queryCriterias.reverse.mkString(" and ")}"

    sql -> queryParams
  }

  def findByFields(pClass: String, fields: Seq[PersistedField]): sci.Seq[PersistedClass] = {
    val pfCols = Seq(
      PF.HolderId, PF.FieldId, PF.BaseFieldId,
      PF.Name, PF.ValueLong, PF.ValueDouble, PF.ValueString,
      PF.TypeName
    )
    val (sql, queryParams) = createByExampleQuery(pClass, pfCols, fields)

    log.debug(s"find request: ${fields}")
    log.debug(s"Generated query: ${sql}")
    log.debug(s"parameter map: ${queryParams.getValues}")

    val allFields = jdbcTemplate.query(sql, queryParams, fieldRowMapper())
    inferPersistedClassesFromFields(pClass, allFields.asScala)
  }

  def deleteByFields(pClass: String, fields: Seq[PersistedField]): Long = {
    val cols = Seq(PF.HolderId)
    val (sql, queryParams) = createByExampleQuery(pClass, cols, fields)
    val classIds = jdbcTemplate.queryForList(sql, queryParams, classOf[java.lang.Long]).asScala.toList.distinct
    log.debug(s"Found ${classIds.size} class entries to be deleted: ${classIds}")

    if (classIds.size > 0) {
      val sql = s"delete from ${PC.Table} where ${PC.Id} in (:deleteIds)"
      val paramMap = new MapSqlParameterSource()
      paramMap.addValue("deleteIds", classIds.asJava)
      jdbcTemplate.update(sql, paramMap)
    } else {
      0L
    }
  }

}
