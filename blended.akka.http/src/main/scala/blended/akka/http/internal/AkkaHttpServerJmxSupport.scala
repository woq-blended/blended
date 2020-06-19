package blended.akka.http.internal

import blended.jmx._
import javax.management.{InstanceNotFoundException, ObjectName}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class AkkaHttpServerJmxSupport(facade : BlendedMBeanServerFacade, exporter : OpenMBeanExporter) {

  def objName: JmxObjectName = JmxObjectName(properties = Map("type" -> "AkkaHttpServer"))

  def readFromJmx(svr : BlendedMBeanServerFacade) : Try[AkkaHttpServerInfo] = {

    def getOptionalAttr[T](info: JmxBeanInfo, name : String)(implicit tag : ClassTag[T]) : Try[Option[T]] = Try {
      info.attributes.value.get(name) match {
        case None => throw new NoSuchAttributeException(info.objName, name)
        case Some(x) => x match {
          case UnitAttributeValue(_) => None
          case v : T => Some(v)
          case _ => throw new InvalidAttributeTypeException(info.objName, name, tag.runtimeClass)
        }
      }
    }

    svr.mbeanInfo(objName).map { info =>
      val host = getOptionalAttr[StringAttributeValue](info, "host").get.map(_.value)
      val sslHost = getOptionalAttr[StringAttributeValue](info, "host").get.map(_.value)
      val port = getOptionalAttr[IntAttributeValue](info, "port").get.map(_.value)
      val sslPort = getOptionalAttr[IntAttributeValue](info, "sslPort").get.map(_.value)
      val routes : List[String] = getOptionalAttr[ListAttributeValue](info, "routes").get.map(_.value).getOrElse(List.empty).map(_.value.toString)
      AkkaHttpServerInfo(host = host, port = port, sslHost = sslHost, sslPort = sslPort, routes = routes.toArray)
    }
  }

  def updateInJmx(update : AkkaHttpServerInfo => AkkaHttpServerInfo) : Unit = Try {
    readFromJmx(facade) match {
      case Success(info) =>
        exporter.`export`(update(info), new ObjectName(objName.objectName), replaceExisting = true)
      case Failure(e) if e.isInstanceOf[InstanceNotFoundException] =>
        exporter.`export`(update(AkkaHttpServerInfo()), new ObjectName(objName.objectName), replaceExisting = false)
      case f => f
    }
  }
}
