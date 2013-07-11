package de.woq.kaufland.sib.util;

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
