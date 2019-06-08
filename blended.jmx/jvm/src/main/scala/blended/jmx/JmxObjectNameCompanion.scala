package blended.jmx

import javax.management.ObjectName

import scala.collection.mutable
import scala.util.Try

class InvalidObjectNameException extends Exception("Expected a concrete object name, the given value is a pattern")
object JmxObjectNameCompanion {

  def createJmxObjectName(objName : ObjectName) : Try[JmxObjectName] = Try {

    if (objName.isPattern) {
      throw new InvalidObjectNameException()
    } else {

      val dom : String = objName.getDomain()
      val props : mutable.Map[String, String] = mutable.Map.empty
      objName.getKeyPropertyList().forEach( (k,v) => props.put(k,v) )
      JmxObjectName(dom, props.toMap)
    }
  }

}
