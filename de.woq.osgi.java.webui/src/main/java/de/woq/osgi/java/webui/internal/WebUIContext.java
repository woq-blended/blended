package de.woq.osgi.java.webui.internal;

import org.osgi.service.http.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;

public class WebUIContext implements HttpContext {

  private final static Logger LOG = LoggerFactory.getLogger(WebUIContext.class);
  @Override
  public boolean handleSecurity(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
    LOG.info("handleSecurity [{}]", httpServletRequest.getRequestURI() );
    return true;
  }

  @Override
  public URL getResource(String s) {
    LOG.info("getResource [{}]", s);
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getMimeType(String s) {
    LOG.info("getMimeType [{}]", s);
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
