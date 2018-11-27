package blended.persistence

import java.{ util => ju }

/**
 * Service to persist JSON-like data structures.
 * To avoid bindings to any kind of JSON library, the supported data structure is kept as Java Map.

 * The supported value types are:
 * - `null`
 * - [[java.lang.Boolean]]
 * - [[java.lang.Long]]
 * - [[java.lang.Double]]
 * - [[java.lang.String]]
 * - [[java.util.Collection]]
 * - [[java.util.Map]] (with String keys)
 *
 * TODO: implement some type-class mechanism for easier use.
 */
trait PersistenceService {

  def persist(pClass: String, data: ju.Map[String, _ <: AnyRef]): ju.Map[String, _ <: AnyRef]

  def findAll(pClass: String): Seq[ju.Map[String, _ <: AnyRef]]

  def findByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Seq[ju.Map[String, _ <: AnyRef]]

  def deleteByExample(pClass: String, data: ju.Map[String, _ <: AnyRef]): Long

}
