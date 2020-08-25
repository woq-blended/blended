package blended.akka.http.internal

import blended.jmx._
import scala.util.Try
import scala.reflect.ClassTag

object AkkaHttpServerJmxSupport {
  val objName : JmxObjectName = JmxObjectName(properties = Map("type" -> "AkkaHttpServer"))
}

class AkkaHttpNamingStrategy extends NamingStrategy {
  override val objectName: PartialFunction[Any,JmxObjectName] = {
    case _ : AkkaHttpServerInfo => AkkaHttpServerJmxSupport.objName
  }
}

class AkkaHttpServerJmxSupport(mbeanManager : ProductMBeanManager) {

  private var current : AkkaHttpServerInfo = AkkaHttpServerInfo()

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

    svr.mbeanInfo(AkkaHttpServerJmxSupport.objName).map { info =>
      val host = getOptionalAttr[StringAttributeValue](info, "host").get.map(_.value)
      val sslHost = getOptionalAttr[StringAttributeValue](info, "host").get.map(_.value)
      val port = getOptionalAttr[IntAttributeValue](info, "port").get.map(_.value)
      val sslPort = getOptionalAttr[IntAttributeValue](info, "sslPort").get.map(_.value)
      val routes : List[String] = getOptionalAttr[ListAttributeValue](info, "routes").get.map(_.value).getOrElse(List.empty).map(_.value.toString)
      AkkaHttpServerInfo(host = host, port = port, sslHost = sslHost, sslPort = sslPort, routes = routes.toArray)
    }
  }

  def updateInJmx(update : AkkaHttpServerInfo => AkkaHttpServerInfo) : Unit = {
    current = update(current)
    mbeanManager.updateMBean(current)
  }
}
