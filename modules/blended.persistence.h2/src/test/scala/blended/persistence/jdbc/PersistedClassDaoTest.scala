package blended.persistence.jdbc

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import de.tobiasroeser.lambdatest.proxy.TestProxy
import javax.sql.DataSource

import scala.jdk.CollectionConverters._

class PersistedClassDaoTest extends LoggingFreeSpec {

  private[this] val log = Logger[this.type]

  "createByExampleQuery" - {

    val ds : DataSource = TestProxy.proxy(classOf[DataSource])
    val dao = new PersistedClassDao(ds)

    "find by top-level id" in {

      val (query, params) = dao.createByExampleQuery("class", Seq("c1"), Seq(
        PersistedField(fieldId = 1, name = "id", valueLong = Some(101L), typeName = TypeName.Long)
      ))

      log.info("query: " + query)
      log.info("params: " + params.getValues())

      val expectedQuery = "select field.c1 " +
        "\nfrom PersistedClass cls, PersistedField field, PersistedField f1 " +
        "\nwhere field.holderId = cls.id and cls.name = :clsName " +
        "and f1.holderId = field.holderId and f1.name = :f1Name and f1.typeName = :f1TypeName " +
        "and f1.valueLong = :f1ValueLong"

      assert(query === expectedQuery)

      val expectedParams = Map(
        "clsName" -> "class",
        "f1Name" -> "id",
        "f1TypeName" -> "Long",
        "f1ValueLong" -> 101L
      )

      assert(params.getValues().asScala === expectedParams)

    }

    "find by inner-field id" in {

      val (query, params) = dao.createByExampleQuery("class", Seq("c1", "id"), Seq(
        PersistedField(fieldId = 1, name = "inner", typeName = TypeName.Object),
        PersistedField(fieldId = 2, name = "id2", baseFieldId = Some(1), valueString = Some("id1"), typeName = TypeName.String)
      ))

      log.info("query: " + query)
      log.info("params: " + params.getValues())

      val expectedQuery = "select field.c1, field.id " +
        "\nfrom PersistedClass cls, PersistedField field, PersistedField f1, PersistedField f2 " +
        "\nwhere field.holderId = cls.id and cls.name = :clsName " +
        "and f1.holderId = field.holderId and f1.name = :f1Name and f1.typeName = :f1TypeName " +
        "and f2.holderId = field.holderId and f2.name = :f2Name and f2.typeName = :f2TypeName " +
        "and f2.valueString = :f2ValueString " +
        "and f2.baseFieldId = f1.fieldId"

      assert(query === expectedQuery)

      val expectedParams = Map(
        "clsName" -> "class",
        "f1Name" -> "inner",
        "f1TypeName" -> "Object",
        "f2Name" -> "id2",
        "f2TypeName" -> "String",
        "f2ValueString" -> "id1"
      )

      assert(params.getValues().asScala === expectedParams)

    }

  }

}
