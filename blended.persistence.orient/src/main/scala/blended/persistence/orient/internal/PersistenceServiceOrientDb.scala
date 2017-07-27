package blended.persistence.orient.internal

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import blended.persistence.PersistenceService
import org.slf4j.LoggerFactory

class PersistenceServiceOrientDb(dbPool: OPartitionedDatabasePool)
    extends PersistenceService {

  private[this] val log = LoggerFactory.getLogger(classOf[PersistenceServiceOrientDb])

  private[this] var createdClasses: Set[String] = Set()

  def withDb[T](f: ODatabaseDocument => T): T = {
    val dbTx = dbPool.acquire()
    try {
      val t = f(dbTx)
      t
    } catch {
      case e: Throwable =>
        throw e
    } finally {
      dbTx.close();
    }
  }

  def withDbTx[T](f: ODatabaseDocument => T): T = {
    val dbTx = dbPool.acquire()
    try {
      val db = dbTx.begin()
      val t = f(db)
      dbTx.commit()
      t
    } catch {
      case e: Throwable =>
        dbTx.rollback()
        throw e
    } finally {
      dbTx.close();
    }
  }

  override def persist(pClass: String, data: java.util.Map[String, _ <: AnyRef]): java.util.Map[String, _ <: AnyRef] = {
    log.debug("About to persist pClass [{}] with data [{}]", Array[Object](pClass, data): _*)
    withDb { db =>
      ensureClassCreated(pClass)
      val doc = new ODocument(pClass)
      data.asScala.foreach {
        case (k, v) => doc.field(k, v)
      }
      val result = doc.save().toMap()
      log.debug("persisted as {}: {}", Array[Object](pClass, result): _*)
      result
    }
  }

  protected[internal] def ensureClassCreated(pClass: String): Unit = {
    if (createdClasses.find(_ == pClass).isEmpty) {
      withDb { db =>
        val existingClass = Option(db.getMetadata().getSchema().getClass(pClass))
        if (existingClass.isEmpty) {
          log.debug("Creating schema for class: {}", pClass)
          db.getMetadata().getSchema().createClass(pClass)
        }
      }
    }
  }

  override def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]] = {
    log.debug("About to findAll for pClass [{}]", pClass)
    withDb { db =>
      ensureClassCreated(pClass)
      val r = db.browseClass(pClass)
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

  override def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]] = {
    log.debug("About to findByExample for pClass [{}] and example data [{}]", Array[Object](pClass, data): _*)
    withDb { db =>
      ensureClassCreated(pClass)
      val ordered = data.asScala.toList
      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
      val values = ordered.map { case (k, v) => v }
      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
      log.debug("About to query: {} with values: {}", Array[Object](sql, values): _*)
      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
      log.debug("Found {} entries", r.size())
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

  def deleteByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Long = {
    log.debug("About to deleteByExample for pClass [{}] and example data [{}]", Array[Object](pClass, data): _*)
    withDb { db =>
      ensureClassCreated(pClass)
      val ordered = data.asScala.toList
      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
      val values = ordered.map { case (k, v) => v }
      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
      log.debug("About to query: {} with values: {}", Array[Object](sql, values): _*)
      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
      val count = r.size()
      log.debug("Found {} entries", count)
      r.iterator().asScala.foreach(d => d.delete())
      count
    }
  }

}