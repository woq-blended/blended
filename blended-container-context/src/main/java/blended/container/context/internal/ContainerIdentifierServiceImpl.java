/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.container.context.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blended.container.context.ContainerContext;
import blended.container.context.ContainerIdentifierService;
import blended.util.ManagedServiceSupport;

public class ContainerIdentifierServiceImpl
		extends ManagedServiceSupport
		implements ContainerIdentifierService {

	private final static String SERVICE_PID = ContainerIdentifierService.class.getPackage().getName();

	private final static String PROP_UUID = "UUID";
	private final static String PROP_PROPERTY = "property.";

	private final Logger log = LoggerFactory.getLogger(ContainerIdentifierServiceImpl.class);

	private final ContainerContext containerContext;

	private String uuid = null;
	private Properties properties = new Properties();

	private AtomicBoolean initialized = new AtomicBoolean(false);

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
	public ContainerContext getContainerContext() {
		return containerContext;
	}

	@Override
	protected ServiceRegistration[] registerServices(final Dictionary<String, ?> incomingProperties) {

		final Properties incoming = new Properties();

		if (incomingProperties != null) {
			for (final Enumeration<String> keys = incomingProperties.keys(); keys.hasMoreElements();) {
				final String key = keys.nextElement();
				incoming.setProperty(key, incomingProperties.get(key).toString());
			}
		}

		if (initialized.get()) {
			updateIdentifier(incoming, false);
		}

		final String[] classes = new String[] {
				ContainerIdentifierService.class.getName()
		};

		final Dictionary<String, Object> svcProps = new Hashtable<String, Object>();

		svcProps.put(PROP_UUID, getUUID());
		for (final String key : getProperties().stringPropertyNames()) {
			svcProps.put(key, getProperties().getProperty(key));
		}

		return new ServiceRegistration[] {
				getBundleContext().registerService(classes, this, svcProps)
		};
	}

	@Override
	public String getUUID() {
		return uuid;
	}

	@Override
	public Properties getProperties() {
		return properties;
	}

	synchronized private void updateIdentifier(final Properties incoming_props, final boolean initialize) {

		boolean requiresSave = false;

		if (initialize != initialized.get()) {

			final Object incoming_uid = incoming_props.get(PROP_UUID);

			// UUID id not set externally
			if (incoming_uid == null) {
				if (uuid == null) {
					uuid = UUID.randomUUID().toString();
					requiresSave = true;
				}
			} else {
				log.info("Incoming UUID = " + incoming_uid.toString());
				// external uuid set
				if (uuid == null) {
					uuid = incoming_uid.toString();
				} else if (!incoming_uid.toString().equals(uuid)) {
					log.error("External uuid configuration does not match container's uuid. Settings ignored ...");
					return;
				}
			}

			log.info("Container identifier is [" + uuid + "]");

			final Properties backup_props = properties;
			properties = new Properties();

			for (final String key : incoming_props.stringPropertyNames()) {
				final String value = incoming_props.getProperty(key);

				if (key.startsWith(PROP_PROPERTY) && key.length() > PROP_PROPERTY.length()) {
					final String realKey = key.substring(PROP_PROPERTY.length());
					properties.setProperty(realKey, value);

					if (backup_props.get(realKey) != null && backup_props.getProperty(realKey).equals(value)) {
						requiresSave = true;
					}
					log.info(String.format("Set identifier property [%s] to [%s]", realKey, value));
				}
			}

			if (requiresSave) {
				final Properties toStore = containerContext.readConfig(SERVICE_PID);
				toStore.setProperty(PROP_UUID, getUUID());
				containerContext.writeConfig(SERVICE_PID, toStore);
			}
		}
	}

}
