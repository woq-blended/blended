package blended.samples.spray.helloworld.internal

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.shiro.SecurityUtils
import org.apache.shiro.subject.Subject
import org.slf4j.LoggerFactory

import blended.spray.SprayOSGIServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import spray.routing.Route
import spray.routing.Directive0

class HelloServlet extends SprayOSGIServlet with HelloService {

  private[this] lazy val log = LoggerFactory.getLogger(classOf[HelloServlet])

  val EnvKey = getClass().getName() + ".SUBJECT"
  
  var subjectByRequest: Map[HttpServletRequest, Subject] = Map()

  
  override protected def requirePermission(permission: String): Directive0 = mapInnerRoute { inner =>
    log.debug("checking required permission: {}", permission)
    Try {
      log.debug("servlet context: attribute names: {}", servletConfig.getServletContext().getAttributeNames())
            val subject = getServletContext().getAttribute(EnvKey).asInstanceOf[Subject]
            log.debug("subject: {}", subject)
            subject.checkPermission(permission)
    } match {
      case Success(_) =>
        log.debug("checked: permitted")
        inner
      case Failure(e) =>
        log.debug("missing required permission: {}", permission)
        failWith(e)
    }
  }

  override def service(hsRequest: HttpServletRequest, hsResponse: HttpServletResponse): Unit = {
    val subject = SecurityUtils.getSubject()
    subjectByRequest
    log.debug("subject: {}", subject)
    getServletContext().setAttribute(EnvKey, subject)
    super.service(hsRequest, hsResponse)
    getServletContext().setAttribute(EnvKey, null)
  }
}
