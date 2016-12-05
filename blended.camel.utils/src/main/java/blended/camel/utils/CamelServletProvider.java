package blended.camel.utils;

import org.apache.camel.http.common.CamelServlet;

public interface CamelServletProvider {

  public CamelServlet getCamelServlet();
}
