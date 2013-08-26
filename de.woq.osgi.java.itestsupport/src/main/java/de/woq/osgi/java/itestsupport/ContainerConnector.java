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

package de.woq.osgi.java.itestsupport;

import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContainerConnector {

  private final String jmxHost;
  private final int jmxPort;

  private AtomicBoolean connected = new AtomicBoolean(false);

  private JMXConnector jmxConnector = null;

  public ContainerConnector(String jmxHost, int port) throws Exception {
    this.jmxHost = jmxHost;
    this.jmxPort = port;

    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi");
    jmxConnector = JMXConnectorFactory.connect(url);
  }

  public synchronized void connect() {
    if (!connected.getAndSet(true)) {
      try {
        jmxConnector.connect();
      } catch (Exception e) {
        connected.set(false);
      }
    }
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

  public static void main(String[] args) {

    try {
      ContainerConnector connector = new ContainerConnector("localhost", 9990);
      MBeanInfo info = connector.getMBeanInfo("de.woq.osgi.java:type=ShutdownBean");
      MBeanOperationInfo[] operations = info.getOperations();
      System.out.println(operations.length);

      connector.invoke("de.woq.osgi.java:type=ShutdownBean", "shutdown");
      connector.disconnect();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
