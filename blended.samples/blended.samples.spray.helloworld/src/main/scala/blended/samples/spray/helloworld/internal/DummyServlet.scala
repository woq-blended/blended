package blended.samples.spray.helloworld.internal

import javax.servlet.{GenericServlet, ServletRequest, ServletResponse}

class DummyServlet extends GenericServlet {
  override def service(servletRequest: ServletRequest, servletResponse: ServletResponse): Unit = {}
}
