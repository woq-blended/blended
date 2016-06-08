package blended.persistence.orient

import org.scalatest.FreeSpec
import org.scalatest.Matchers
import blended.testsupport.TestFile
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import blended.persistence.orient.internal.PersistenceServiceOrientDb

class OrientExperimentalTest extends FreeSpec with Matchers with TestFile {

  implicit val deletePolicy = TestFile.DeleteNever

  "TEST one insert" in {
    withTestDir() { dir =>
      val url = "plocal:" + dir.getPath() + "/db1"
      new ODatabaseDocumentTx(url).create()

      val pool = new OPartitionedDatabasePool(url, "admin", "admin")
      val exp = new PersistenceServiceOrientDb(pool)

      try {
        exp.withDb { db =>
          val doc = new ODocument("Person")
          doc.field("name", "Luke")
          doc.field("surname", "Skywalker")
          doc.save()
        }

        exp.withDb { db =>
          val data = db.browseClass("Person").iterator().asScala.toSeq
          data should have size (1)
          data.head.field[String]("name") should equal("Luke")
          data.head.field[String]("surname") should equal("Skywalker")
        }

      } finally {
        pool.close()
      }

    }
  }

  "TEST 2" in {

    withTestDir() { dir =>
      val url = "plocal:" + dir.getPath() + "/db2"
      new ODatabaseDocumentTx(url).create()
      val pool = new OPartitionedDatabasePool(url, "admin", "admin")
      val exp = new PersistenceServiceOrientDb(pool)
      try {
        val persisted = exp.persist("CLASS1", Map("key1" -> "value1").asJava)
        persisted.get("@class") should equal("CLASS1")
        persisted.get("key1") should equal("value1")

        val found = exp.findAll("CLASS1")
        found should have size (1)
        val first = found.head
        first.get("@class") should equal("CLASS1")
        first.get("key1") should equal("value1")

        val example = Map("key1" -> "value1").asJava
        val byExample = exp.findByExample("CLASS1", example)
        byExample should have size (1)
        byExample.head.get("@class") should equal("CLASS1")
        byExample.head.get("key1") should equal("value1")

      } finally {
        pool.close()
      }

    }
  }

  "Test 3 with complex Config Object" in {

    val config = ConfigFactory.parseString("""
			  |key1=value1
			  |key2=[ kv1, kv2 ]
			  |key3 {
			  |  k3a=v3a
			  |  k3b=[ v3b1, v3b2 ]
			  |}""".stripMargin)

    withTestDir() { dir =>
      config.root().unwrapped()
      val url = "plocal:" + dir.getPath() + "/db3"
      new ODatabaseDocumentTx(url).create()
      val pool = new OPartitionedDatabasePool(url, "admin", "admin")
      val exp = new PersistenceServiceOrientDb(pool)
      try {
        exp.persist("CONFIG", config.root().unwrapped())
        val loaded = exp.findAll("CONFIG")

        loaded should have size (1)
        loaded.head.get("@class") should equal("CONFIG")

        loaded.head.asScala.-("@class").-("@rid").asJava should equal(config.root().unwrapped())

      } finally {
        pool.close()
      }
    }
  }

}