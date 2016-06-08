package blended.persistence.orient.internal

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.query.OQuery
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import blended.persistence.orient.PersistenceService

class PersistenceServiceOrientDb(dbPool: OPartitionedDatabasePool)
    extends PersistenceService {

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
    withDb { db =>
      val doc = new ODocument(pClass)
      data.asScala.foreach {
        case (k, v) => doc.field(k, v)
      }
      doc.save().toMap()
    }
  }

  override def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]] = {
    withDb { db =>
      val r = db.browseClass(pClass)
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

  override def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]] = {
    withDb { db =>
      val ordered = data.asScala.toList
      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
      val values = ordered.map { case (k, v) => v }
      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
      r.iterator().asScala.map(d => d.toMap).toList
    }
  }

}