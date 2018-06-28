package blended.persistence.h2.internal

import blended.testsupport.TestFile
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import blended.persistence.jdbc.PersistenceServiceJdbc
import blended.persistence.jdbc.PersistedClassDao
import org.springframework.transaction.PlatformTransactionManager

class PersistenceServiceJdbcTest
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

      DbFactory.withDataSource(dir, "db") { dataSource =>
        val dao = new PersistedClassDao(dataSource)
        val txMgr = new DummyPlatformTransactionManager()
        dao.init()
        val exp = new PersistenceServiceJdbc(txMgr, dao)
        val empty = exp.findAll("CONFIG")
        empty should have size (0)

        exp.persist("CONFIG", config.root().unwrapped())
        val loaded = exp.findAll("CONFIG")

        loaded should have size (1)
        loaded.head.get("@class") should equal("CONFIG")

        loaded.head.asScala.-("@class").-("@rid").asJava should equal(config.root().unwrapped())
      }

      DbFactory.withDataSource(dir, "db") { dataSource =>
        val dao = new PersistedClassDao(dataSource)
        val txMgr = new DummyPlatformTransactionManager()
        dao.init()
        val exp = new PersistenceServiceJdbc(txMgr, dao)
        val one = exp.findAll("CONFIG")
        one should have size (1)

        exp.persist("CONFIG", config2.root().unwrapped())
        val two = exp.findAll("CONFIG")

        two should have size (2)
      }

    }
  }

}