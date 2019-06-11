package blended.jmx

import scala.util.Try

class InvalidObjectNameFormatException(name : String) extends Exception(s"Value [$name] is not a valid object name")

object JmxObjectName {

  private def parseMap(s : String)(objName : String) : Try[Map[String, String]] = Try {
    val props : Array[(String, String)] = s.split(",")
      .map{ p =>
        val keyVal : Array[String] = { p.split("=") }

        if (keyVal.length == 2) {
          keyVal(0) -> keyVal(1)
        } else {
          throw new InvalidObjectNameFormatException(objName)
        }
      }
    props.toMap
  }

  def apply(s : String) : Try[JmxObjectName] = Try {

    require(Option(s).isDefined)

    val parts : Array[String] = s.split(":")

    if (parts.length == 2) {
      JmxObjectName(parts(0), parseMap(parts(1))(s).get)
    } else {
      throw new InvalidObjectNameFormatException(s)
    }
  }
}

case class JmxObjectName (
  domain : String,
  properties : Map[String,String]
) {

  val sortedProps : List[String] = properties.toList.sorted.map{ case (k,v) => s"$k=$v" }
  val objectName : String = s"$domain:${sortedProps.mkString(",")}"

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
