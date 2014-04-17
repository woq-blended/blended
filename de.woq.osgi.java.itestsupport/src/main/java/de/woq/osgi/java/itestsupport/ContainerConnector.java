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

package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.itestsupport.condition.MBeanMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.BadAttributeValueExpException;
import javax.management.BadBinaryOpValueExpException;
import javax.management.BadStringOperationException;
import javax.management.InvalidApplicationException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContainerConnector {

  private final String jmxHost;
  private final int jmxPort;

  private AtomicBoolean connected = new AtomicBoolean(false);

  private final JMXServiceURL serviceUrl;
  private JMXConnector jmxConnector = null;

  private final static Logger LOGGER = LoggerFactory.getLogger(ContainerConnector.class);

  public ContainerConnector(String jmxHost, int port) throws Exception {
    this.jmxHost = jmxHost;
    this.jmxPort = port;

    serviceUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi");
  }

  public synchronized JMXConnector connect() {
    if (!connected.getAndSet(true)) {
      try {
        jmxConnector = JMXConnectorFactory.connect(serviceUrl);
        jmxConnector.connect();
      } catch (Exception e) {
        connected.set(false);
      }
    }
    return jmxConnector;
  }

  public synchronized void disconnect() {
    if (connected.getAndSet(false)) {
      try {
        jmxConnector.close();
      } catch (Exception e) {
        connected.set(true);
      }
    }
  }

  public Map<ObjectName, MBeanInfo> getMBeanInfo(final MBeanMatcher matcher) throws Exception {

    Map<ObjectName, MBeanInfo> result = new HashMap<>();

    connect();

    Set<ObjectName> objectNames = jmxConnector.getMBeanServerConnection().queryNames(null, null);

    for(ObjectName name: objectNames) {
      MBeanInfo info = jmxConnector.getMBeanServerConnection().getMBeanInfo(name);

      if (info != null && matcher.matchesMBean(name, info)) {
        LOGGER.info("Found MBean [{}]", name.toString());
        result.put(name, info);
      }
    }

    return result;
  }

  public MBeanInfo getMBeanInfo(final String objectName) throws Exception {
    return getMBeanInfo(new ObjectName(objectName));
  }

  public MBeanInfo getMBeanInfo(final ObjectName objectName) {

    connect();

    MBeanInfo result = null;

    if (connected.get()) {
      try {
        result = jmxConnector.getMBeanServerConnection().getMBeanInfo(objectName);
      } catch (Exception e) {
        disconnect();
      }
    }

    return result;
  }

  public Object invoke(final String objectName, final String methodName, final Object...params) throws Exception {

    Object result = null;

    connect();

    MBeanInfo info = getMBeanInfo(objectName);
    MBeanOperationInfo[] operations = info.getOperations();

    for(MBeanOperationInfo op: operations) {
      if (op.getName().equals(methodName)) {
        if (op.getSignature().length == params.length) {

          String[] signature = new String[op.getSignature().length];
          int i=0;

          for(MBeanParameterInfo param: op.getSignature()) {
            signature[i] = param.getType();
            i++;
          }

          result = jmxConnector.getMBeanServerConnection().invoke(
            new ObjectName(objectName),
            methodName,
            params,
            signature
          );
        }
      }
    }

    return result;
  }

  @Override
  public String toString() {
    return "ContainerConnector[" + jmxHost + "," + jmxPort + "]";
  }
}
