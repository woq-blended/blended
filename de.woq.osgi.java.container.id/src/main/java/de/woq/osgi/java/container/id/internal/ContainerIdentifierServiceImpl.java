/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.woq.osgi.java.container.id.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.woq.osgi.java.container.context.ContainerContext;
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

  private final ContainerContext containerContext;

  private String uuid = null;
  private Properties properties = new Properties();

  private AtomicBoolean initialized = new AtomicBoolean(false);

  private final static Logger LOGGER = LoggerFactory.getLogger(ContainerIdentifierServiceImpl.class);

  public ContainerIdentifierServiceImpl(final BundleContext bundleContext, final ContainerContext containerContext) {
    super(bundleContext);
    this.containerContext = containerContext;

    updateIdentifier(containerContext.readConfig(getServicePid()), true);
    initialized.set(true);
  }

  @Override
  protected String getServicePid() {
    return SERVICE_PID;
  }

  @Override
  protected ServiceRegistration registerService(Dictionary<String, ?> incomingProperties) {
    if (getServiceRegistration() == null) {

      Properties incoming = new Properties();
      for(Enumeration<String> keys = incomingProperties.keys(); keys.hasMoreElements();) {
        String key = keys.nextElement();
        incoming.setProperty(key, incomingProperties.get(key).toString());
      }

      if (initialized.get()) {
        updateIdentifier(incoming, false);
      }

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

  synchronized private void updateIdentifier(Properties incoming_props, boolean initialize) {

    if (initialize != initialized.get()) {

      Object incoming_uid = incoming_props.get(PROP_UUID);

      // UUID id not set externally
      if (incoming_uid == null) {
        if (uuid == null) {
          uuid = UUID.randomUUID().toString();
        }
      } else {
        LOGGER.info("Incoming UUID = " + incoming_uid.toString());
        // external uuid set
        if (uuid == null) {
          uuid = incoming_uid.toString();
        } else if (!incoming_uid.toString().equals(uuid)) {
          LOGGER.error("External uuid configuration does not match container's uuid. Settings ignored ...");
          return;
        }
      }

      LOGGER.info("Container identifier is [" + uuid + "]");


      Properties backup_props = properties;
      properties = new Properties();

      int exists = 0;

      properties.clear();

      for(String key: incoming_props.stringPropertyNames()) {
        String value = incoming_props.getProperty(key);

        if (key.startsWith(PROP_PROPERTY) && key.length() > PROP_PROPERTY.length()) {
          String realKey = key.substring(PROP_PROPERTY.length());
          properties.setProperty(realKey, value);

          if (backup_props.get(realKey) != null && backup_props.getProperty(realKey).equals(value)) {
            exists++;
          }
          LOGGER.info(String.format("Set identifier property [%s] to [%s]", realKey, value));
        }
      }

      if (exists != properties.size()) {
        Properties toStore = new Properties();
        for(String key: properties.stringPropertyNames()) {
          toStore.setProperty(PROP_PROPERTY + key, properties.getProperty(key));
        }
        toStore.setProperty(PROP_UUID, getUUID());

        containerContext.writeConfig(SERVICE_PID, toStore);
      }
    }
  }

}
