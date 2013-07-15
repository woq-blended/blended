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

package de.woq.osgi.java.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.ops4j.pax.runner.Run;

public class Main implements ContainerConstants {

  private Properties properties = null;
  private String containerName = "empty";

  private void launch(final String[] args) {

    parseArguments(args);
    System.out.println("Starting container configuration [" + getContainerName() + "]");

    List<String> params = new ArrayList<String>();

    params.add("--nologo=true");
    params.add("--clean");

    params.add("--platform=" + getPlatformVendor());
    params.add("--version=" + getPlatformVersion());
    params.add("--startLevel=" + getPlatformStartLevel());

    params.add("--log=" + getPlatformLogLevel());

    String jvmOptions = getJvmOptions();
    if (jvmOptions != null)
    {
      params.add("--vmOptions=" + jvmOptions);
    }

    String woqHome = getPlatformProperties().getProperty(PROP_WOQ_HOME);
    if (woqHome != null)
    {
      params.add("--classpath=" + woqHome + "/config");
    }

    params.add("--dir=" + getContainerName());

    params.add("classpath:" + getContainerName() + ".composite");

    Run.main(params.toArray(new String[] {}));
  }

  private String getContainerName() {
    return containerName;
  }

  private void setContainerName(String containerName) {
    this.containerName = containerName;
  }

  private String getPlatformVendor()
  {
    return getPlatformProperties().getProperty(PROP_PLATFORM_VENDOR, DEFAULT_PLATFORM_VENDOR);
  }

  private String getPlatformVersion()
  {
    return getPlatformProperties().getProperty(PROP_PLATFORM_VERSION, DEFAULT_PLATFORM_VERSION);
  }

  private String getPlatformLogLevel()
  {
    return getPlatformProperties().getProperty(PROP_LOG_LEVEL, DEFAULT_LOG_LEVEL);
  }

  private String getPlatformStartLevel()
  {
    return getPlatformProperties().getProperty(PROP_PLATFORM_STARTLEVEL, DEFAULT_PLATFORM_STARTLEVEL);
  }

  private String getStackSize()
  {
    return getPlatformProperties().getProperty(JVM_STACK_SIZE, DEFAULT_JVM_STACK_SIZE);
  }

  private String getMinHeap()
  {
    return getPlatformProperties().getProperty(JVM_MIN_HEAP, DEFAULT_JVM_MIN_HEAP);
  }

  private String getMaxHeap()
  {
    return getPlatformProperties().getProperty(JVM_MIN_HEAP, DEFAULT_JVM_MAX_HEAP);
  }

  private String getJvmOptions()
  {
    StringBuilder optionString = new StringBuilder();

    optionString.append("-Xss");
    optionString.append(getStackSize());
    optionString.append(" ");

    optionString.append("-Xms");
    optionString.append(getMinHeap());
    optionString.append(" ");

    optionString.append("-Xmx");
    optionString.append(getMaxHeap());
    optionString.append(" ");

    for(String key: getPlatformProperties().stringPropertyNames())
    {
      if (key.startsWith(PARAM_SYSPROP))
      {
        String realKey = key.substring(PARAM_SYSPROP.length());
        String value = getPlatformProperties().getProperty(key);
        optionString.append("-D");
        optionString.append(realKey);
        optionString.append("=");
        optionString.append(value);
        optionString.append(" ");
      }
    }

    String sibHome = getPlatformProperties().getProperty(PROP_WOQ_HOME);
    if (sibHome != null)
    {
      optionString.append("-D");
      optionString.append(PROP_WOQ_HOME);
      optionString.append("=");
      optionString.append(sibHome);
      optionString.append(" ");
    }

    String debugPort = getPlatformProperties().getProperty(JVM_DEBUG_PORT);

    if (debugPort != null)
    {
      String suspend = getPlatformProperties().getProperty(JVM_DEBUG_SUSPEND, "n");
      optionString.append("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=");
      optionString.append(suspend);
      optionString.append(",address=");
      optionString.append(debugPort);
      optionString.append(" ");
    }

    return optionString.length() > 0 ? optionString.toString() : null;
  }

  private Properties getPlatformProperties() {

    if (properties == null) {
      properties = new Properties();
      try {
        System.out.println("Loading back to [" + CONTAINER_CFG_FILE + "]");
        properties.load(Main.class.getResourceAsStream("/" + getContainerName() + "." + CONTAINER_CFG_FILE));
      } catch(Exception e) {
        try {
          System.out.println("Falling back to [" + CONTAINER_CFG_FILE + "]");
          properties.load(Main.class.getResourceAsStream("/" + CONTAINER_CFG_FILE));
        } catch (Exception inner) {
          System.out.println("Unable to load container properties file.");
        }
      }
    }
    return properties;
  }

  private void parseContainer(final String[] args) {

    List<String> ctArgs  = new ArrayList<String>();

    for (int i = 0; i<args.length; i++) {
      if (args[i].startsWith("-" + PARAM_SYSPROP)) {
        i++;
      } else {
        ctArgs.add(args[i]);
      }
    }

    if (ctArgs.size() > 0) {
      setContainerName(ctArgs.get(0));
    }
  }

  private List<String> parseArguments(final String[] args) {

    parseContainer(args);

    List<String> result = new ArrayList<String>();

    Properties props = getPlatformProperties();

    for (int i = 0; i<args.length; i++)
    {
      if (args[i].startsWith("-" + PARAM_SYSPROP))
      {
        String propName = args[i];
        i++;
        String propValue = i < args.length ? args[i] : "";

        props.setProperty(propName.substring(1), propValue);
      }
      else
      {
        result.add(args[i]);
      }
    }

    return result;
  }

  public static void main(final String[] args)
  {
    new Main().launch(args);
  }
}
