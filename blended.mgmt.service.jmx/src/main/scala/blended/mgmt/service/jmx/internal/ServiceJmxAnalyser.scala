package blended.mgmt.service.jmx.internal

import javax.management.openmbean.{CompositeData, CompositeDataSupport}

import scala.collection.JavaConverters._
import javax.management.{Attribute, MBeanServer, ObjectInstance, ObjectName}

import blended.updater.config.ServiceInfo
import org.slf4j.LoggerFactory

class ServiceJmxAnalyser(server: MBeanServer, config: ServiceJmxConfig) {

  private val log = LoggerFactory.getLogger(classOf[ServiceJmxAnalyser])

  def createFilter(svcConfig : SingleServiceConfig, template: ServiceTypeTemplate) : ObjectName = {

    val templateAttrs = template.query.foldLeft(""){ case (s, (k,v)) => s + k + "=" + v + "," }
    val attr = svcConfig.query.foldLeft(templateAttrs){ case (s, (k,v)) => s + k + "=" + v + ","}

    new ObjectName(template.domain + ":" + attr + "*")
  }

  def instances(svcConfig: SingleServiceConfig) : List[(ObjectInstance, SingleServiceConfig, ServiceTypeTemplate)] = {

    (config.templates.get(svcConfig.svcType)) match {

      case None =>
        log.warn(s"No Servicetype Template for service ${svcConfig.name} found")
        List.empty

      case Some(template) =>
        server.queryMBeans(createFilter(svcConfig, template), null).asScala.toList.map((_, svcConfig, template))

    }
  }

  def serviceInfo(instance: ObjectInstance, svcConfig: SingleServiceConfig, template: ServiceTypeTemplate) : ServiceInfo = {

    def transformOne(name: List[String], value: Object) : List[(String, String)] = value match {
      case cd if cd.isInstanceOf[CompositeDataSupport] => {
        val data = cd.asInstanceOf[CompositeDataSupport]
        data.getCompositeType().keySet().asScala.map { k =>
          data.get(k) match {
            case innerCd if innerCd.isInstanceOf[CompositeData] => transformOne(name ::: List(k), innerCd)
            case v => List( (name.mkString(".") + "." + k, v.toString()) )
          }
        }.toList.flatten
      }
      case a => List( (name.mkString("."), a.toString() ) )
    }

    def transformAll(name: List[String], attrs: List[Attribute]) : List[(String, String)] = attrs match {
      case ( head :: tail ) =>
        val newName = name ::: List(head.getName())
        transformOne(newName, head.getValue) ::: transformAll(name, tail)
      case Nil => List.empty
    }

    def attributes() : Map[String, String] = {

      val attrNames = template.attributes ++ svcConfig.attributes

      val attrList = server.getAttributes(instance.getObjectName(), attrNames.toArray).asScala.toList.map(_.asInstanceOf[Attribute])

      transformAll(List.empty, attrList).toMap
    }

    ServiceInfo(
      name = instance.getObjectName().toString(),
      serviceType = template.name,
      timestampMsec = System.currentTimeMillis(),
      lifetimeMsec = config.interval * 1000l,
      props = attributes()
    )

  }

  def analyse() : List[ServiceInfo] = {

    val start = System.currentTimeMillis()

    val objectInstances  =
      config.services.map{ case (_, svcConfig) => instances(svcConfig) }.toList.flatten

    val result = objectInstances.map { case (i, s, t) => serviceInfo(i, s, t) }

    val end = System.currentTimeMillis()

    log.debug(s"Analysed ${result.size} Service Infos in ${end - start}ms")

    result
  }
}
