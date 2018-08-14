package blended.persistence

import java.{ util => ju }

trait PersistenceService {

  def persist(pClass: String, data: ju.Map[String, _ <: AnyRef]): ju.Map[String, _ <: AnyRef]

  def findAll(pClass: String): Seq[ju.Map[String, _ <: AnyRef]]

  def findByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Seq[ju.Map[String, _ <: AnyRef]]

  def deleteByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Long
  
}
