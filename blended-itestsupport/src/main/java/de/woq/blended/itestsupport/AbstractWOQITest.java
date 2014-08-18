/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

import de.woq.blended.util.FileReader;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

public abstract class AbstractWOQITest {

  private static boolean external = true;

  private final static String PROP_HOST         = "woq.container.host";
  private final static String PROP_FILE         = "woq.container.properties";
  private final static String DEFAULT_PROP_FILE = "itest.properties";

  private final static Properties testProperties = new Properties();

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
  public void waitForConditions() {

  }

  protected Properties getTestProperties() {
    return testProperties;
  }

  protected boolean isExternal() {
    return external;
  }

  protected String getContainerHost() {
    return isExternal() ? System.getProperty(PROP_HOST, "localhost") : "localhost";
  }
}
