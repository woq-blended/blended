/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.blended.util;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ManagedServiceSupport implements ManagedService
{

  private final BundleContext bundleContext;

  private ServiceRegistration managedService;
  private ServiceRegistration serviceRegistration;

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedServiceSupport.class);

  protected abstract String getServicePid();

  protected abstract ServiceRegistration registerService(Dictionary<String, ?> properties);

  public ManagedServiceSupport(final BundleContext context)
  {
    this.bundleContext = context;
  }

  public final BundleContext getBundleContext() {
    return bundleContext;
  }

  public final ServiceRegistration getServiceRegistration() {
    return serviceRegistration;
  }

  public final void setServiceRegistration(ServiceRegistration serviceRegistration) {
    this.serviceRegistration = serviceRegistration;
  }


  public void init()  {
    managedService = bundleContext.registerService(ManagedService.class.getName(), this, getDefaultConfig());
  }

  public void destroy()
  {
    managedService.unregister();
    deregisterService();
  }

  protected final Dictionary<String, Object> getDefaultConfig()
  {
    Dictionary<String, Object> result = new Hashtable<String, Object>();
    result.put(Constants.SERVICE_PID, getServicePid());
    return result;
  }

  synchronized protected void deregisterService()
  {
    if (serviceRegistration != null)
    {
      LOGGER.debug("Deregistering Service for [" + getServicePid() + "]");
      serviceRegistration.unregister();
      serviceRegistration = null;
    }
  }

  public final void updated(Dictionary properties) throws ConfigurationException
  {
    LOGGER.info("Updating configuration for [" + getServicePid() + "]");
    deregisterService();
    serviceRegistration = registerService(properties);
  }
}
