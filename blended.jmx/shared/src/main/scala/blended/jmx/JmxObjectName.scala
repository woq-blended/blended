package blended.jmx

import javax.management.ObjectName

import scala.util.Try
import scala.collection.JavaConverters._

class InvalidObjectNameFormatException(name : String) extends Exception(s"Value [${name}] is not a valid object name")

object JmxObjectName {

  private def parseMap(s : String)(objName : String) : Try[Map[String, String]] = Try {
    val props : Array[(String, String)] = s.split(",")
      .map{ p =>
        val keyVal : Array[String] = { p.split("=") }

        if (keyVal.length == 2 && keyVal.forall(_.trim.length > 0)) {
          keyVal(0).trim -> keyVal(1).trim
        } else {
          throw new InvalidObjectNameFormatException(objName)
        }
      }
    props.toMap
  }

  def apply(s : String) : Try[JmxObjectName] = Try {

    Option(s) match {
      case None => throw new InvalidObjectNameFormatException("null")
      case Some(name) =>
        val parts : Array[String] = s.split(":")

        if (parts.length == 2) {
          JmxObjectName(parts(0), parseMap(parts(1))(name).get)
        } else {
          throw new InvalidObjectNameFormatException(s)
        }
    }
  }

  def fromObjName(objName : ObjectName) : JmxObjectName = {

    val keys : Seq[String] = objName.getKeyPropertyList().keys.asScala.toSeq

    JmxObjectName(
      domain = objName.getDomain(),
      properties = (for (k <- keys) yield (k, objName.getKeyProperty(k))).toMap
    )
  }
}

case class JmxObjectName (
  domain : String,
  properties : Map[String,String]
) {

  val sortedProps : List[String] = properties.toList.sorted.map{ case (k,v) => s"$k=$v" }
  val objectName : ObjectName = new ObjectName(s"$domain:${sortedProps.mkString(",")}")
  val objectNamePattern : ObjectName = new ObjectName(s"$domain:${sortedProps.mkString(",")},*")

  override val toString: String = {
    s"${getClass().getSimpleName()}($objectName)"
  }

  def isAncestor(other : JmxObjectName) : Boolean = {
    domain.equals(other.domain) &&
    properties.size < other.properties.size &&
    properties.forall{ case (k,v) => other.properties(k).equals(v) }
  }

  def isParent(other: JmxObjectName) : Boolean =
    isAncestor(other) && (properties.size == other.properties.size - 1)

  def differingKeys(other: JmxObjectName) : List[String] = {
    if (properties.size < other.properties.size) {
      other.differingKeys(this)
    } else {
      properties.filter { case (k,v) =>
        other.properties.get(k).isEmpty || !other.properties(k).equals(v)
      }.keys.toList
    }
  }
}
