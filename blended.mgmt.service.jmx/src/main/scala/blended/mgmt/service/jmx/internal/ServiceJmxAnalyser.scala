package blended.mgmt.service.jmx.internal

import scala.collection.JavaConverters._
import javax.management.{MBeanServer, ObjectInstance, ObjectName}

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

    ServiceInfo(
      name = instance.getObjectName().toString(),
      serviceType = template.name,
      timestampMsec = System.currentTimeMillis(),
      lifetimeMsec = config.interval,
      props = Map.empty
    )

  }

  def extractInfo(instance : ObjectInstance, svcConfig: SingleServiceConfig, template: ServiceTypeTemplate, ttl: Long) : ServiceInfo = {
    ServiceInfo(
      name = instance.getObjectName().toString(),
      serviceType = template.name,
      timestampMsec = System.currentTimeMillis(),
      lifetimeMsec = ttl,
      props = Map.empty
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
