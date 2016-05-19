package blended.camel.utils

import javax.servlet.Servlet

import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.apache.camel.component.servlet.CamelHttpTransportServlet

class CamelServletActivator extends DominoActivator with TypesafeConfigWatching {

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idService) =>

      val alias = cfg.getString("alias")
      val servletName = cfg.getString("servletName")

      val servlet = new CamelHttpTransportServlet()
      val provider = new DefaultCamelServletProvider()
      provider.setCamelServlet(servlet)

      servlet.providesService[Servlet] { "alias" -> alias }
      provider.providesService[CamelServletProvider] { "servlet-Name" -> servletName }
    }
  }
}
