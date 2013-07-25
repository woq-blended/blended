package de.woq.osgi.java.webui.internal;

import de.woq.osgi.java.container.context.ContainerContext;
import org.osgi.service.http.HttpService;

import javax.servlet.Servlet;

public class WebUIInitializer {

  private final String alias;
  private HttpService httpService;
  private Servlet servlet;
  private ContainerContext containerContext;

  public WebUIInitializer(String alias) {
    this.alias = alias;
  }

  public void init() throws Exception  {
    WebUIContext httpContext = new WebUIContext(alias);
    httpContext.setContainerContext(getContainerContext());

    httpService.registerServlet("/", getServlet(), null, httpContext);
    httpService.registerResources(alias, "/", httpContext);
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

  public ContainerContext getContainerContext() {
    return containerContext;
  }

  public void setContainerContext(ContainerContext containerContext) {
    this.containerContext = containerContext;
  }
}
