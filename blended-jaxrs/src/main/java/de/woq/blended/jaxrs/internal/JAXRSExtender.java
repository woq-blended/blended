/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.blended.jaxrs.internal;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import de.woq.blended.jaxrs.JAXRSResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.ServiceUnavailableException;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.ws.rs.core.Application;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JAXRSExtender {

  private HttpService httpService = null;
  private Map<ServiceReference, String> resourceMap = new HashMap<ServiceReference, String>();
  private final static Logger LOGGER = LoggerFactory.getLogger(JAXRSExtender.class);

  public void bind(ServiceReference ref) throws Exception {
    String servletName = (String)ref.getProperty("servlet-name");
    String alias = (String)ref.getProperty("alias");

    if (alias == null)
      alias = "/" + servletName;

    if (alias == null)
      throw new Exception("Neither alias nor servlet-name is set for the OSGi service");

    Bundle bundle = ref.getBundle();

    LOGGER.info("Binding service for Bundle [" + bundle.getSymbolicName() + "]");

    try {
      JAXRSResource jaxrsResource = (JAXRSResource)bundle.getBundleContext().getService(ref);
      getHttpService().registerServlet(alias, createResourceServlet(jaxrsResource), null, null);
      resourceMap.put(ref, alias);
    } catch (Exception e) {
      LOGGER.error("Error registering servlet", e);
    } finally {
    }
  }

  public void unbind(ServiceReference ref) {
    try {
      String alias = resourceMap.get(ref);
      if (alias != null) {
        LOGGER.info("Deregistering Servlet with alias [" + alias + "]");
        getHttpService().unregister(alias);
      }
    } catch (ServiceUnavailableException sue) {
    }
  }

  public HttpService getHttpService() {
    return httpService;
  }

  public void setHttpService(HttpService httpService) {
    this.httpService = httpService;
  }

  private Servlet createResourceServlet(final JAXRSResource resource) {
    return new ServletContainer(createApplication(resource));
  }

  private Application createApplication(final JAXRSResource resource) {
    Application result = new DefaultResourceConfig() {
      @Override
      public Set<Object> getRootResourceSingletons() {
        Set<Object> rootResources = new HashSet<Object>();
        rootResources.add(resource);
        return rootResources;
      }
    };
    return result;
  }
}
