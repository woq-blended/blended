package blended.persistence.orient.internal

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter

import blended.persistence.PersistenceService
import blended.util.logging.Logger
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

class PersistenceServiceOrientDb(dbPool: OPartitionedDatabasePool)
    extends PersistenceService {

  private[this] val log = Logger[PersistenceServiceOrientDb]

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
    log.debug(s"About to persist pClass [${pClass}] with data [${data}]")
    withDb { db =>
      ensureClassCreated(pClass)
      val doc = new ODocument(pClass)
      data.asScala.foreach {
        case (k, v) => doc.field(k, v)
      }
      val result = doc.save().toMap()
      log.debug(s"persisted as ${pClass}: ${result}")
      result
    }
  }

  protected[internal] def ensureClassCreated(pClass: String): Unit = {
    if (createdClasses.find(_ == pClass).isEmpty) {
      withDb { db =>
        val existingClass = Option(db.getMetadata().getSchema().getClass(pClass))
        if (existingClass.isEmpty) {
          log.debug(s"Creating schema for class: ${pClass}")
          db.getMetadata().getSchema().createClass(pClass)
        }
      }
    }
  }

  override def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]] = {
    log.debug(s"About to findAll for pClass [${pClass}]")
    withDb { db =>
      ensureClassCreated(pClass)
      val r = db.browseClass(pClass)
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

  override def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]] = {
    log.debug(s"About to findByExample for pClass [${pClass}] and example data [${data}]")
    withDb { db =>
      ensureClassCreated(pClass)
      val ordered = data.asScala.toList
      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
      val values = ordered.map { case (k, v) => v }
      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
      log.debug(s"About to query: ${sql} with values: ${values}")
      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
      log.debug(s"Found ${r.size()} entries")
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

  def deleteByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Long = {
    log.debug(s"About to deleteByExample for pClass [${pClass}] and example data [${data}]")
    withDb { db =>
      ensureClassCreated(pClass)
      val ordered = data.asScala.toList
      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
      val values = ordered.map { case (k, v) => v }
      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
      log.debug(s"About to query: ${sql} with values: ${values}")
      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
      val count = r.size()
      log.debug(s"Found ${count} entries")
      r.iterator().asScala.foreach(d => d.delete())
      count
    }
  }

}