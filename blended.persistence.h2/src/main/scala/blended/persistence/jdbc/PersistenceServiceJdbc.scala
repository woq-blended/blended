package blended.persistence.jdbc

import blended.persistence.PersistenceService
import java.{ util => ju }
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

/**
 * Generic implementation of a PersistenceService using a JDBC DataSource.
 *
 * Idea:
 *
 */
class PersistenceServiceJdbc(
  txManager: PlatformTransactionManager,
  dao: PersistedClassDao
)
  extends PersistenceService {

  //  def this(dataSource: DataSource) = this(
  //    new DataSourceTransactionManager(dataSource),
  //    new PersistedClassDao(dataSource)
  //  )

  private[this] val log = org.log4s.getLogger

  private[this] val txTemplate = new TransactionTemplate(txManager)
  private[this] val txTemplateRo = {
    val t = new TransactionTemplate(txManager)
    t.setReadOnly(true)
    t
  }

  override def deleteByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Long = {
    ???
  }

  override def findAll(pClass: String): Seq[ju.Map[String, _ <: AnyRef]] = {
    val classes = txTemplateRo.execute { ts =>
      dao.findAll(pClass)
    }
    classes.map(pc => PersistedField.toJuMap(pc.fields))
  }

  override def findByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Seq[ju.Map[String, _ <: AnyRef]] = {
    ???
  }

  override def persist(pClass: String, data: ju.Map[String, _ <: AnyRef]): ju.Map[String, _ <: AnyRef] = {
    // TODO check if data already contains id

    log.debug(s"About to persist class [${pClass}] with data [${data}]")

    val unpersisted = PersistedClass(
      id = None,
      name = pClass,
      fields = PersistedField.extractFieldsWithoutDataId(data)
    )
    log.debug(s"About to persist [${unpersisted}]")

    val persisted = txTemplate.execute { ts =>
      dao.persist(unpersisted)
    }

    PersistedField.toJuMap(persisted.fields)
  }
  //
  //  private[this] var createdClasses: Set[String] = Set()
  //
  //  def withDb[T](f: ODatabaseDocument => T): T = {
  //    val dbTx = dbPool.acquire()
  //    try {
  //      val t = f(dbTx)
  //      t
  //    } catch {
  //      case e: Throwable =>
  //        throw e
  //    } finally {
  //      dbTx.close();
  //    }
  //  }
  //
  //  def withDbTx[T](f: ODatabaseDocument => T): T = {
  //    val dbTx = dbPool.acquire()
  //    try {
  //      val db = dbTx.begin()
  //      val t = f(db)
  //      dbTx.commit()
  //      t
  //    } catch {
  //      case e: Throwable =>
  //        dbTx.rollback()
  //        throw e
  //    } finally {
  //      dbTx.close();
  //    }
  //  }
  //
  //  protected[internal] def ensureClassCreated(pClass: String): Unit = {
  //    if (createdClasses.find(_ == pClass).isEmpty) {
  //      withDb { db =>
  //        val existingClass = Option(db.getMetadata().getSchema().getClass(pClass))
  //        if (existingClass.isEmpty) {
  //          log.debug("Creating schema for class: {}", pClass)
  //          db.getMetadata().getSchema().createClass(pClass)
  //        }
  //      }
  //    }
  //  }
  //
  //  override def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]] = {
  //    log.debug("About to findAll for pClass [{}]", pClass)
  //    withDb { db =>
  //      ensureClassCreated(pClass)
  //      val r = db.browseClass(pClass)
  //      r.iterator().asScala.map(d => d.toMap).toList
  //    }
  //  }
  //
  //  override def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]] = {
  //    log.debug("About to findByExample for pClass [{}] and example data [{}]", Array[Object](pClass, data): _*)
  //    withDb { db =>
  //      ensureClassCreated(pClass)
  //      val ordered = data.asScala.toList
  //      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
  //      val values = ordered.map { case (k, v) => v }
  //      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
  //      log.debug("About to query: {} with values: {}", Array[Object](sql, values): _*)
  //      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
  //      log.debug("Found {} entries", r.size())
  //      r.iterator().asScala.map(d => d.toMap).toList
  //    }
  //  }
  //
  //  def deleteByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Long = {
  //    log.debug("About to deleteByExample for pClass [{}] and example data [{}]", Array[Object](pClass, data): _*)
  //    withDb { db =>
  //      ensureClassCreated(pClass)
  //      val ordered = data.asScala.toList
  //      val placeholder = ordered.map { case (k, v) => s" ${k} = ? " }
  //      val values = ordered.map { case (k, v) => v }
  //      val sql = s"select * from ${pClass} where ${placeholder.mkString(" and ")}"
  //      log.debug("About to query: {} with values: {}", Array[Object](sql, values): _*)
  //      val r: java.util.List[ODocument] = db.query(new OSQLSynchQuery(sql), values.toArray: _*)
  //      val count = r.size()
  //      log.debug("Found {} entries", count)
  //      r.iterator().asScala.foreach(d => d.delete())
  //      count
  //    }
  //  }

}