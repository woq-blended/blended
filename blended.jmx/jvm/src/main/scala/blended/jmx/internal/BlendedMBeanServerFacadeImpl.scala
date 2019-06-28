package blended.jmx.internal

import blended.jmx._
import javax.management._
import prickle.Pickle
import blended.jmx.json.PrickleProtocol._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class BlendedMBeanServerFacadeImpl(svr : MBeanServer) extends BlendedMBeanServerFacade {

  override def mbeanNames(): Try[List[JmxObjectName]] = Try {
    val names : mutable.ListBuffer[ObjectName] = mutable.ListBuffer.empty[ObjectName]
    // scalastyle:off null
    svr.queryNames(null, null).forEach(n => names.append(n))
    // scalastyle:on null
    names.toList.map(on => JmxObjectNameCompanion.createJmxObjectName(on).get)
  }

  def mbeanInfo(objName : JmxObjectName) : Unit = {

    val start = System.currentTimeMillis()

    val name : ObjectName = new ObjectName(objName.objectName)
    val info : MBeanInfo = svr.getMBeanInfo(name)

    val readableAttrs : Map[String, MBeanAttributeInfo] = info.getAttributes().filter(_.isReadable()).map(a => a.getName() -> a).toMap
    val attrNames : Array[String] = readableAttrs.values.map(_.getName()).toArray
    val attrs : List[Attribute] = svr.getAttributes(name, attrNames).asList().asScala.toList

    val attributes : Map[String, AttributeValue] = attrs.map { a =>
      val v = a.getValue()
      (a. getName(), AttributeValue.lift(v).get)
    }.toMap

    val obj : JmxObject = JmxObject(objName, CompositeAttributeValue(attributes))


    val json : String = Pickle.intoString(obj)
  }
}
