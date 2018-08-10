package blended.persistence.jdbc

import java.{ util => ju }

import blended.persistence.PersistenceService
import blended.util.logging.Logger
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

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

  private[this] val log = Logger[PersistenceServiceJdbc]

  private[this] val txTemplate = new TransactionTemplate(txManager)
  private[this] val txTemplateRo = {
    val t = new TransactionTemplate(txManager)
    t.setReadOnly(true)
    t
  }

  override def deleteByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Long = {
    log.debug(s"About to delete by example pClass: ${pClass}, data: ${data}")
    val fields = PersistedField.extractFieldsWithoutDataId(data)
    val unsupportedFields = fields.filter { field => field.baseFieldId.isDefined }
    if (unsupportedFields.isEmpty) {
      txTemplate.execute { ts =>
        dao.deleteByFields(pClass, fields)
      }
    } else {
      log.error(s"deleteByExample is currently not implemented for hierarchical nested fields. Offending fields: ${unsupportedFields}")
      0L
    }

  }

  override def findAll(pClass: String): Seq[ju.Map[String, _ <: AnyRef]] = {
    val classes = txTemplateRo.execute { ts =>
      dao.findAll(pClass)
    }
    classes.map(pc => PersistedField.toJuMap(pc.fields))
  }

  override def findByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Seq[ju.Map[String, _ <: AnyRef]] = {
    val fields = PersistedField.extractFieldsWithoutDataId(data)
    val queryFields = fields.flatMap { field =>
      field.baseFieldId match {
        case Some(baseFieldId) =>
          log.error(s"findByExample is currently not implemented for hierarchical nested fields. The following field will be ignored: ${field}")
          Seq()
        case None =>
          // we examime a top-level field
          Seq(field)
      }
    }

    val classes = txTemplateRo.execute { ts =>
      dao.findByFields(pClass, queryFields)
    }

    // select cId, ...
    // from pclass
    // where

    classes.map(pc => PersistedField.toJuMap(pc.fields))
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

}