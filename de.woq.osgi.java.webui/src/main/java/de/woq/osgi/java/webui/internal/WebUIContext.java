package de.woq.osgi.java.webui.internal;

import de.woq.osgi.java.container.context.ContainerContext;
import org.osgi.service.http.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class WebUIContext implements HttpContext {

  private final String alias;
  private ContainerContext containerContext;
  private final static Logger LOG = LoggerFactory.getLogger(WebUIContext.class);

  public WebUIContext(final String alias) {
    this.alias = alias;
  }

  @Override
  public boolean handleSecurity(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
    //allow everything for now
    return true;
  }

  @Override
  public URL getResource(String resource) {
    LOG.debug("Resolving Resource [{}]", resource);

    URL result = null;

    if (containerContext != null) {
      File f = new File(getContainerContext().getContainerDirectory() + getAlias() + resource);

      if (f.exists() && !f.isDirectory() && f.canRead()) {
        try {
          result = new URL("file://" + f.getAbsolutePath());
        } catch (MalformedURLException e) {
          LOG.error("Error creating resource URL [{}]", e);
        }
      } else {
        LOG.warn("File [{}] is not accessible.", f.getAbsolutePath());
      }
    }

    return result;
  }

  @Override
  public String getMimeType(String s) {

    LOG.info("getMimeType [{}]", s);
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public ContainerContext getContainerContext() {
    return containerContext;
  }

  public void setContainerContext(ContainerContext containerContext) {
    this.containerContext = containerContext;
  }

  public String getAlias() {
    return alias;
  }
}
