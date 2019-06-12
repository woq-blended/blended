package blended.jmx.internal

import blended.jmx.{AttributeValue, BlendedMBeanServerFacade, JmxAttribute, JmxObject, JmxObjectName, JmxObjectNameCompanion}
import javax.management.openmbean.CompositeDataSupport
import javax.management.{Attribute, MBeanInfo, MBeanServer, ObjectName}
import prickle.Pickle

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

    val attrNames : Array[String] = info.getAttributes().filter(_.isReadable()).map(_.getName())
    val attrs : List[Attribute] = svr.getAttributes(name, attrNames).asList().asScala.toList

    val obj : JmxObject = JmxObject(objName, attrs.map{ a =>
      val v = a.getValue()

      if (!v.isInstanceOf[CompositeDataSupport] && !v.isInstanceOf[Array[_]]) {
        val o : AttributeValue = AttributeValue.lift(v).get
        println(s"$objName(${a.getName()}) : $o")
      }

      JmxAttribute(a.getName(), AttributeValue.lift(a.getValue()).get)

    })


    val json : String = Pickle.intoString(obj)
    println(s"\n\n${System.currentTimeMillis() - start} : $json")
    return
  }
}
