package de.woq.osgi.java.webui.internal;

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

import javax.servlet.Servlet;

public class WebUIInitializer {

  private HttpService httpService;
  private Servlet servlet;

  public void init() throws Exception  {
    HttpContext httpContext = new WebUIContext();
    httpService.registerServlet("/", getServlet(), null, httpContext);
    httpService.registerResources("/static", "/", httpContext);
  }

  public void destroy() {
    httpService.unregister("/woq");
  }

  public HttpService getHttpService() {
    return httpService;
  }

  public void setHttpService(HttpService httpService) {
    this.httpService = httpService;
  }

  public Servlet getServlet() {
    return servlet;
  }

  public void setServlet(Servlet servlet) {
    this.servlet = servlet;
  }
}
