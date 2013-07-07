/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.java.mgmt_rest.internal;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

import de.woq.osgi.java.jaxrs.JAXRSResource;
import de.woq.osgi.java.mgmt_core.BundleInfo;
import de.woq.osgi.java.mgmt_core.OSGIManagementService;

@Path("/bundles")
public class OsgiManagementResource implements JAXRSResource {

  private OSGIManagementService osgiManagementService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<BundleInfo> listBundles() {
    return osgiManagementService.listBundles();
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public BundleInfo bundleInfo(@PathParam("id") long id) {
    return osgiManagementService.getInfo(id);
  }

  public OSGIManagementService getOsgiManagementService() {
    return osgiManagementService;
  }

  public void setOsgiManagementService(OSGIManagementService osgiManagementService) {
    this.osgiManagementService = osgiManagementService;
  }
}
