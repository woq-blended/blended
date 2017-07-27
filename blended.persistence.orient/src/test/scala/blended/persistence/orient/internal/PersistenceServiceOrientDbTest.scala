package blended.persistence.orient.internal

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.ConfigFactory
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import blended.testsupport.TestFile
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.collection.JavaConverters._
import java.io.File

class PersistenceServiceOrientDbTest
    extends FreeSpec
    with Matchers
    with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "TEST data survives db close and reopen" in {

    val config = ConfigFactory.parseString("""
			  |key1=value1
			  |key2=[ kv1, kv2 ]
			  |key3 {
			  |  k3a=v3a
			  |  k3b=[ v3b1, v3b2 ]
			  |}""".stripMargin)

    val config2 = ConfigFactory.parseString("""
    				|key1=value2
    				|key2=[ kv3, kv4 ]
    				|key3 {
    				|  k3a=v3a2
    				|  k3b=[ v3b22, v3b22 ]
    				|}""".stripMargin)

    withTestDir(new java.io.File("target/tmp")) { dir =>
      config.root().unwrapped()
      val url = "plocal:" + dir.getPath() + "/db"
      new File(dir, "db").mkdirs()

      val db = new ODatabaseDocumentTx(url)
      try {
        db.create()
      } finally {
        db.close()
      }

      {
        val pool = new OPartitionedDatabasePool(url, "admin", "admin")
        val exp = new PersistenceServiceOrientDb(pool)
        try {
          val empty = exp.findAll("CONFIG")
          empty should have size (0)

          exp.persist("CONFIG", config.root().unwrapped())
          val loaded = exp.findAll("CONFIG")

          loaded should have size (1)
          loaded.head.get("@class") should equal("CONFIG")

          loaded.head.asScala.-("@class").-("@rid").asJava should equal(config.root().unwrapped())

        } finally {
          pool.close()
        }
      }

      {
        val pool = new OPartitionedDatabasePool(url, "admin", "admin")
        val exp = new PersistenceServiceOrientDb(pool)
        try {
          val one = exp.findAll("CONFIG")
          one should have size (1)

          exp.persist("CONFIG", config2.root().unwrapped())
          val two = exp.findAll("CONFIG")

          two should have size (2)

        } finally {
          pool.close()
        }
      }

    }
  }

}