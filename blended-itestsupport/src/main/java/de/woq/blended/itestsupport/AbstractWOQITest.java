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

package de.woq.blended.itestsupport;

import de.woq.blended.itestsupport.condition.Condition;
import de.woq.blended.itestsupport.condition.ConditionCanConnect;
import de.woq.blended.itestsupport.condition.ConditionMBeanExists;
import de.woq.blended.itestsupport.condition.ConditionWaiter;
import de.woq.blended.util.FileReader;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class AbstractWOQITest {

  private static ContainerRunner runner = null;

  private static boolean external = false;

  private final static String PROP_EXTERNAL     = "woq.container.external";
  private final static String PROP_HOST         = "woq.container.host";
  private final static String PROP_JMX_PORT     = "woq.container.jmxport";
  private final static String PROP_FILE         = "woq.container.properties";
  private final static String DEFAULT_PROP_FILE = "itest.properties";

  private final static Properties testProperties = new Properties();

  @BeforeClass
  public static void external() {
    String e = System.getProperty(PROP_EXTERNAL, "false");
    external = e.equalsIgnoreCase("true");
  }

  @BeforeClass
  public static void loadTestConfig() {

    InputStream is = null;

    try {
      byte[] testPropertyFile = FileReader.readFile(System.getProperty(PROP_FILE, DEFAULT_PROP_FILE));

      is = new ByteArrayInputStream(testPropertyFile);

      testProperties.load(is);

    } catch (Exception e) {
      // ignore
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  @Before
  synchronized public void startContainer() throws Exception {

    if (!isExternal() && runner == null) {
      final ContainerProfile profile = getContainerProfile();

      runner = new ContainerRunner(profile.name());
      runner.start();

      ConditionWaiter.waitOnCondition(
        profile.timeout() * 1000l,
        1000l,
        startConditions(runner).toArray(new Condition[]{})
      );
    }
  }

  @Before
  public void waitForConditions() {

  }

  @AfterClass
  synchronized public static void stopContainer() throws Exception {
    if (!external && runner != null) {
      runner.stop();
      runner.waitForStop();
    }
  }

  protected Properties getTestProperties() {
    return testProperties;
  }

  protected boolean isExternal() {
    return external;
  }

  protected ContainerRunner getContainerRunner() {
    return runner;
  }

  protected List<Condition> startConditions(final ContainerRunner runner) {

    List<Condition> result = new ArrayList<>();

    result.add(new ConditionCanConnect(getContainerHost(), getJmxPort()));
    result.add(new ConditionMBeanExists(runner, "de.woq.osgi.java", "de.woq.osgi.java.container.context.internal.ContainerShutdown"));

    return result;
  }

  protected int getJmxPort() {

    int result = 1099;

    if (!isExternal()) {
      result = runner.findJMXPort();
    } else {
      String p = System.getProperty(PROP_JMX_PORT, "11099");
      try {
        result = Integer.parseInt(p);
      } catch (NumberFormatException e) {
        // ignore
      }
    }

    return result;
  }

  protected String getContainerHost() {
    return isExternal() ? System.getProperty(PROP_HOST, "localhost") : "localhost";
  }

  private  ContainerProfile getContainerProfile() throws Exception {
    return ProfileResolver.resolveProfile(getClass());
  }

}
