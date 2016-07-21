package blended.camel.utils

import java.util
import javax.servlet.ServletContext

import org.apache.camel.http.common.CamelServlet
import org.osgi.framework.{BundleContext, ServiceRegistration}
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

trait CamelOsgiServlet { this : CamelServlet =>

  private[this] val logger = LoggerFactory.getLogger(classOf[CamelOsgiServlet])

  private[this] val servlet : CamelServlet = this
  private[this] var osgiSvc : Option[ServiceRegistration[_]] = None

  def registerOsgi(context: ServletContext) : Unit = {

    val attrs = context.getAttributeNames().asScala.toList.map { key =>
      (key, context.getAttribute(key.toString()))
    }.toMap

    logger.info(s"Servlet context attributes are ${attrs.mkString("[", ",", "]")}")

    val servletName = servlet.getServletName()
    logger.info(s"Trying to register Servlet [$servletName] with OSGI")

    Option(context.getAttribute("osgi-bundlecontext").asInstanceOf[BundleContext]).foreach { bc =>
      val props = new util.Hashtable[String, AnyRef]()
      props.put("servletName", getServletName())

      osgiSvc = Some(bc.registerService(classOf[CamelServlet].getName(), servlet, props ))
      logger.info(s"Registered Servlet [$servletName] with OSGI")
    }
  }

  def unregisterOsgi() : Unit = osgiSvc.foreach(_.unregister())

}

object CamelOsgiServlet