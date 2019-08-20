package blended.jmx

import javax.management.ObjectName

import scala.language.implicitConversions
import scala.collection.mutable
import scala.util.Try

class InvalidObjectNameException extends Exception("Expected a concrete object name, the given value is a pattern")

object JmxObjectNameCompanion {

  implicit def toObjectName(n : JmxObjectName) : ObjectName = new ObjectName(n.objectName)

  def toPattern(name : JmxObjectName) : ObjectName =
    new ObjectName(s"${name.domain}:${name.sortedProps.mkString(",")},*")

  def createJmxObjectName(objName : ObjectName) : Try[JmxObjectName] = Try {

    if (objName.isPattern) {
      throw new InvalidObjectNameException()
    } else {
      val dom : String = objName.getDomain()
      val props : mutable.Map[String, String] = mutable.Map.empty
      objName.getKeyPropertyList().forEach( (k,v) => props.put(k,v))
      JmxObjectName(dom, props.toMap)
    }
  }
}
