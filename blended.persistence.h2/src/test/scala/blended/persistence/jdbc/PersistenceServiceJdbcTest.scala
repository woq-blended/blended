package blended.persistence.jdbc

import java.io.File
import java.{ lang => jl, util => ju }

import scala.collection.JavaConverters._

import blended.testsupport.TestFile
import com.typesafe.config.ConfigFactory
import org.scalactic.source.Position.apply
import org.scalatest.Matchers
import org.springframework.transaction.PlatformTransactionManager

class PersistenceServiceJdbcTest
  extends LoggingFreeSpec
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

  "do not ignore pClass in findAll" in {
    val pClass = "type1"
    val pClass2 = "type2"
    withTestPersistenceService() { (serv, txMgr) =>
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(1L), "color" -> "blau").asJava)
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(2L), "color" -> "red").asJava)

      val all = serv.findAll(pClass2)
      assert(all.size == 0)
    }
  }

  "do not ignore pClass in findByExample" in {
    val pClass = "type1"
    val pClass2 = "type2"
    withTestPersistenceService() { (serv, txMgr) =>
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(1L), "color" -> "blau").asJava)
      serv.persist(pClass, Map("id" -> jl.Long.valueOf(2L), "color" -> "red").asJava)

      val cand = serv.findByExample(pClass2, Map("id" -> jl.Long.valueOf(1L)).asJava)
      assert(cand.size === 0)
    }
  }

  "persist, load and delete" - {

    "simple data" in {
      val pClass = "type1"
      withTestPersistenceService() { (serv, txMgr) =>
        serv.persist(pClass, Map("id" -> jl.Long.valueOf(1L), "color" -> "blau").asJava)
        serv.persist(pClass, Map("id" -> jl.Long.valueOf(2L), "color" -> "red").asJava)

        val all = serv.findAll(pClass)
        assert(all.size == 2)

        {
          // no result
          val cnt = serv.deleteByExample(pClass, Map("id" -> jl.Long.valueOf(3L)).asJava)
          assert(cnt === 0)
        }

        {
          // wrong class
          val cnt = serv.deleteByExample("type2", Map("id" -> jl.Long.valueOf(1L)).asJava)
          assert(cnt === 0)
        }

        {
          // one deleted
          val cnt = serv.deleteByExample(pClass, Map("id" -> jl.Long.valueOf(1L)).asJava)
          assert(cnt === 1)
        }

        {
          // check rest
          val rest = serv.findAll(pClass)
          assert(rest.size === 1)
        }
      }
    }

    "nested data" in {
      val pClass = "type1"
      withTestPersistenceService() { (serv, txMgr) =>
        serv.persist(pClass, Map("inner" -> Map("id" -> jl.Long.valueOf(1L), "color" -> "blau").asJava).asJava)
        serv.persist(pClass, Map("inner" -> Map("id" -> jl.Long.valueOf(2L), "color" -> "red").asJava).asJava)

        val all = serv.findAll(pClass)
        assert(all.size == 2)

        {
          // no result
          val cnt = serv.deleteByExample(pClass, Map("inner" -> Map("id" -> jl.Long.valueOf(3L)).asJava).asJava)
          assert(cnt === 0)
        }

        {
          // wrong class
          val cnt = serv.deleteByExample("type2", Map("inner" -> Map("id" -> jl.Long.valueOf(1L)).asJava).asJava)
          assert(cnt === 0)
        }

        {
          // find same we delete later
          val found = serv.findByExample(pClass, Map("inner" -> Map("id" -> jl.Long.valueOf(1L)).asJava).asJava)
          assert(found.size === 1)
          assert(found(0).get("inner").asInstanceOf[ju.Map[String, _]].get("id") === jl.Long.valueOf(1L))
        }

        {
          // one deleted
          val cnt = serv.deleteByExample(pClass, Map("inner" -> Map("id" -> jl.Long.valueOf(1L)).asJava).asJava)
          assert(cnt === 1)
        }

        {
          // check rest
          val rest = serv.findAll(pClass)
          assert(rest.size === 1)
        }
      }
    }

    "long strings" in {
      val size = 502
      val longString = List.fill(size)("x").mkString
      assert(longString.size === size)

      withTestPersistenceService() { (serv, txMgr) =>
        serv.persist("stringdata", Map("id" -> jl.Long.valueOf(1L), "string" -> longString).asJava)
        val loaded = serv.findByExample("stringdata", Map("id" -> jl.Long.valueOf(1L)).asJava)
        assert(loaded.size === 1)
        assert(loaded(0).asScala("string") === longString)
      }
    }

  }

  "TEST data survives db close and reopen" in {

    val config = ConfigFactory.parseString(
      """
        |key1=value1
        |key2=[ kv1, kv2 ]
        |key3 {
        |  k3a=v3a
        |  k3b=[ v3b1, v3b2 ]
        |}""".stripMargin
    )

    val config2 = ConfigFactory.parseString(
      """
        |key1=value2
        |key2=[ kv3, kv4 ]
        |key3 {
        |  k3a=v3a2
        |  k3b=[ v3b22, v3b22 ]
        |}""".stripMargin
    )

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