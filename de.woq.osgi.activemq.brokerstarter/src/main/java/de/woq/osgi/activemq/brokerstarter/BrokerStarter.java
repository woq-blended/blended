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

package de.woq.osgi.activemq.brokerstarter;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.Service;
import org.apache.activemq.broker.BrokerService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;

import javax.jms.ConnectionFactory;
import java.util.Dictionary;
import java.util.Hashtable;

public class BrokerStarter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerStarter.class);

  private final BrokerService brokerService;
  private BundleContext bundleContext = null;

  public BrokerStarter(final Service brokerService) {
    this.brokerService = (BrokerService)brokerService;
  }

  public void init() {

    try {
      brokerService.waitUntilStarted();
      LOGGER.info("ActiveMQ broker [" + brokerService.getBrokerName() + "] started successfully.");

      final Dictionary<String, String> props = new Hashtable<>();
      props.put("provider", "activemq");
      props.put("brokername", brokerService.getBrokerName());

      bundleContext.registerService(ConnectionFactory.class, createConnectionFactory(brokerService), props);
    } catch (Exception e) {
      LOGGER.error("Failed to start Active MQ broker", e);
    }
  }

  public void destroy() {}

  private ConnectionFactory createConnectionFactory(final BrokerService broker) {

    final String url = "vm://" + broker.getBrokerName() + "?create=false";
    LOGGER.info("Creating ActiveMQ ConnectionFactory for URL [" + url + "}");
    final ActiveMQConnectionFactory amqFactory = new ActiveMQConnectionFactory(url);
    return new CachingConnectionFactory(amqFactory);
  }

  public BundleContext getBundleContext() {
    return bundleContext;
  }

  public void setBundleContext(final BundleContext bundleContext) {
    this.bundleContext = bundleContext;
  }
}
