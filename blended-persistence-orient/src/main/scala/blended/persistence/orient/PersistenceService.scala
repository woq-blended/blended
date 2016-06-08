package blended.persistence.orient

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.query.OQuery
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

trait PersistenceService {

  def persist(pClass: String, data: java.util.Map[String, _ <: AnyRef]): java.util.Map[String, _ <: AnyRef]

  def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]]

  def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]]

}
