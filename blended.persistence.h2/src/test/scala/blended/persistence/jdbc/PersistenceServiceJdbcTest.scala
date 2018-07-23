package blended.persistence.jdbc

import blended.testsupport.TestFile
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import org.scalactic.source.Position.apply
import java.io.File
import java.{ lang => jl }
import org.springframework.transaction.PlatformTransactionManager

class PersistenceServiceJdbcTest
  extends FreeSpec
  with Matchers
  with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  /**
   * Use this to run a test against a pre-initialized Persistence Service. The schema is created, but no data is present.
   */
  def withTestPersistenceService(dir: Option[File] = None)(f: (PersistenceServiceJdbc, PlatformTransactionManager) => Unit): Unit = {

    def worker(dir: File): Unit = {
      DbFactory.withDataSource(dir, "db") { dataSource =>
        val dao = new PersistedClassDao(dataSource)
        val txMgr = new DummyPlatformTransactionManager()
        dao.init()
        val exp = new PersistenceServiceJdbc(txMgr, dao)
        f(exp, txMgr)
      }
    }

    dir match {
      case Some(d) => worker(d)
      case None =>
        withTestDir(new File("target/tmp")) { dir =>
          worker(dir)
        }
    }
  }

  "persist and load simple class" in {
    val pClass = "type1"
    withTestPersistenceService() { (serv, txMgr) =>
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(1L), "color" -> "blau").asJava)
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(2L), "color" -> "red").asJava)
      
      val all = serv.findAll(pClass)
      assert(all.size == 2)
      assert(all.find(m => m.get("id") == jl.Long.valueOf(1L) && m.get("color") == "blau").isDefined)
      
      val cand = serv.findByExample(pClass, Map("id" -> jl.Long.valueOf(1L)).asJava)
      assert(cand.size === 1)
      assert(cand.head.get("id") === 1L)
      assert(cand.head.get("color") === "blau")
    }
  }

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
      // config.root().unwrapped()

      withTestPersistenceService(Some(dir)) { (persistenceService, _) =>
        val empty = persistenceService.findAll("CONFIG")
        empty should have size (0)

        persistenceService.persist("CONFIG", config.root().unwrapped())
        val loaded = persistenceService.findAll("CONFIG")

        loaded should have size (1)
        loaded.head should equal(config.root().unwrapped())
      }

      withTestPersistenceService(Some(dir)) { (persistenceService, _) =>
        val one = persistenceService.findAll("CONFIG")
        one should have size (1)

        persistenceService.persist("CONFIG", config2.root().unwrapped())
        val two = persistenceService.findAll("CONFIG")

        two should have size (2)
      }

    }
  }

}