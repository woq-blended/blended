package blended.jmx.internal

import blended.jmx._
import blended.util.RichTry._
import javax.management._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try
import JmxObjectNameCompanion._
import blended.util.logging.Logger

class BlendedMBeanServerFacadeImpl(svr : MBeanServer) extends BlendedMBeanServerFacade {

  private val log : Logger = Logger[BlendedMBeanServerFacade]

  override def mbeanNames(objName : Option[JmxObjectName]): Try[List[JmxObjectName]] = Try {
    val names : mutable.ListBuffer[ObjectName] = mutable.ListBuffer.empty[ObjectName]
    // scalastyle:off null
    svr.queryNames(objName.map(toPattern).orNull, null).forEach(n => names.append(n))
    // scalastyle:on null
    val result : List[JmxObjectName] = names.toList.map(on => JmxObjectNameCompanion.createJmxObjectName(on).unwrap)

    log.debug(s"Found [${result.size}] MBean names for [$objName].")
    result
  }

  def mbeanInfo(objName : JmxObjectName) : Try[JmxBeanInfo] = Try {
    val info : MBeanInfo = svr.getMBeanInfo(objName)

    log.debug(s"Read MBean Info for [$objName]")

    val readableAttrs : Map[String, MBeanAttributeInfo] =
      info.getAttributes().filter(_.isReadable()).map(a => a.getName() -> a).toMap

    val attrNames : Array[String] = readableAttrs.values.map(_.getName()).toArray
    val attrs : List[Attribute] = svr.getAttributes(objName, attrNames).asList().asScala.toList

    val attributes : Map[String, AttributeValue] = attrs.map { a =>
      val v = a.getValue()
      (a. getName(), JmxAttributeCompanion.lift(v).unwrap)
    }.toMap

    val result = JmxBeanInfo(objName, CompositeAttributeValue(attributes))

    log.debug(s"Read MBean info [$objName] : [$result]")
    result
  }
}
