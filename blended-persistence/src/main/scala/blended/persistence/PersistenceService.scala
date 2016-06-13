package blended.persistence

trait PersistenceService {

  def persist(pClass: String, data: java.util.Map[String, _ <: AnyRef]): java.util.Map[String, _ <: AnyRef]

  def findAll(pClass: String): Seq[java.util.Map[String, _ <: AnyRef]]

  def findByExample(pClass: String, data: java.util.Map[String, _ <: AnyRef]): Seq[java.util.Map[String, _ <: AnyRef]]

}
