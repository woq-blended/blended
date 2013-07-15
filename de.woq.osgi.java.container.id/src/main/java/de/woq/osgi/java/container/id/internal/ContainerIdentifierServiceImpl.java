package de.woq.osgi.java.container.id.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.UUID;

import de.woq.osgi.java.container.id.ContainerIdentifierService;
import de.woq.osgi.java.util.ManagedServiceSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerIdentifierServiceImpl
  extends ManagedServiceSupport
  implements ContainerIdentifierService {

  private final static String SERVICE_PID = ContainerIdentifierService.class.getPackage().getName();

  private final static String PROP_UUID = "UUID";
  private final static String PROP_PROPERTY = "property.";

  private String uuid = null;
  private Properties properties = new Properties();

  private final static Logger LOGGER = LoggerFactory.getLogger(ContainerIdentifierServiceImpl.class);

  public ContainerIdentifierServiceImpl(final BundleContext context) {
    super(context);
  }

  @Override
  protected String getServicePid() {
    return SERVICE_PID;
  }

  @Override
  protected ServiceRegistration registerService(Dictionary<String, ?> properties) {
    if (getServiceRegistration() == null) {

      updateIdentifier(properties);

      String[] classes = new String[] {
        ContainerIdentifierService.class.getName()
      };

      Dictionary<String, Object> svcProps = new Hashtable<String, Object>();

      svcProps.put(PROP_UUID, getUUID());
      for(String key: getProperties().stringPropertyNames()) {
        svcProps.put(key, getProperties().getProperty(key));
      }

      setServiceRegistration(getBundleContext().registerService(classes, this, svcProps));
    }

    return getServiceRegistration();
  }

  @Override
  public String getUUID() {
    return uuid;
  }

  @Override
  public Properties getProperties() {
    return properties;
  }

  synchronized void updateIdentifier(Dictionary<String, ?> incoming_props) {

    Object incoming_uid = properties.get(PROP_UUID);

    // UUID id not set externally
    if (incoming_uid == null) {
      if (uuid == null) {
        uuid = UUID.randomUUID().toString();
      }
      LOGGER.info("Container identifier is [" + uuid + "]");
    } else {
      if (!incoming_uid.toString().equals(uuid)) {
        LOGGER.error("External uuid configuration does not match container's uuid. Settings ignored ...");
        return;
      }
    }

    properties.clear();

    for(Enumeration<String> keys = incoming_props.keys(); keys.hasMoreElements();) {
      String key = keys.nextElement();
      Object value = incoming_props.get(key);

      if (key.startsWith(PROP_PROPERTY) && key.length() > PROP_PROPERTY.length()) {
        String realKey = key.substring(PROP_PROPERTY.length());
        properties.setProperty(realKey, value.toString());
        LOGGER.info(String.format("Set identifier property [%s] to [%s]", realKey, value));
      }
    }
  }

}
