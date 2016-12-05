package blended.camel.utils;

import org.apache.camel.http.common.CamelServlet;

public class DefaultCamelServletProvider implements CamelServletProvider {

  private CamelServlet camelServlet = null;

  public void setCamelServlet(CamelServlet camelServlet) {
    this.camelServlet = camelServlet;
  }

  @Override
  public CamelServlet getCamelServlet() {
    return camelServlet;
  }
}
