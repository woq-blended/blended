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

package de.woq.osgi.java.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.ops4j.pax.runner.Run;

public class WOQContainer implements ContainerConstants {

  private final Properties properties;
  private String containerName = "empty";

  public WOQContainer(final Properties properties) {
    this(properties, null);
  }

  public WOQContainer(final Properties properties, final String containerName) {
    this.properties = properties;
    if (containerName != null) {
      this.containerName = containerName;
    }
  }

  public void launch() {

    System.out.println("Starting container configuration [" + getContainerName() + "]");

    List<String> params = new ArrayList<String>();

    params.add("--nologo=true");
    params.add("--clean");

    params.add("--platform=" + getPlatformVendor());
    params.add("--version=" + getPlatformVersion());
    params.add("--startLevel=" + getPlatformStartLevel());

    params.add("--log=" + getPlatformLogLevel());

    String jvmOptions = getJvmOptions();
    if (jvmOptions != null) {
      System.out.println("Using JVM options: " + jvmOptions);
      params.add("--vmOptions=" + jvmOptions);
    }

    String woqHome = properties.getProperty(PROP_WOQ_HOME);
    if (woqHome != null) {
      params.add("--classpath=" + woqHome + "/config");
    }

    params.add("--dir=" + getContainerName());

    params.add("classpath:" + getContainerName() + ".composite");

    Run.main(params.toArray(new String[]{}));
  }

  protected String getContainerName() {
    return containerName;
  }

  protected String getPlatformVendor() {
    return properties.getProperty(PROP_PLATFORM_VENDOR, DEFAULT_PLATFORM_VENDOR);
  }

  protected String getPlatformVersion() {
    return properties.getProperty(PROP_PLATFORM_VERSION, DEFAULT_PLATFORM_VERSION);
  }

  protected String getPlatformLogLevel() {
    return properties.getProperty(PROP_LOG_LEVEL, DEFAULT_LOG_LEVEL);
  }

  protected String getPlatformStartLevel() {
    return properties.getProperty(PROP_PLATFORM_STARTLEVEL, DEFAULT_PLATFORM_STARTLEVEL);
  }

  protected String getStackSize() {
    return properties.getProperty(JVM_STACK_SIZE, DEFAULT_JVM_STACK_SIZE);
  }

  protected String getMinHeap() {
    return properties.getProperty(JVM_MIN_HEAP, DEFAULT_JVM_MIN_HEAP);
  }

  protected String getMaxHeap() {
    return properties.getProperty(JVM_MIN_HEAP, DEFAULT_JVM_MAX_HEAP);
  }

  protected String getJvmOptions() {
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

    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith(PARAM_SYSPROP)) {
        String realKey = key.substring(PARAM_SYSPROP.length());
        String value = properties.getProperty(key);
        optionString.append("-D");
        optionString.append(realKey);
        optionString.append("=");
        optionString.append(value);
        optionString.append(" ");
      }
    }

    String sibHome = properties.getProperty(PROP_WOQ_HOME);
    if (sibHome != null) {
      optionString.append("-D");
      optionString.append(PROP_WOQ_HOME);
      optionString.append("=");
      optionString.append(sibHome);
      optionString.append(" ");
    }

    String debugPort = properties.getProperty(JVM_DEBUG_PORT);

    if (debugPort != null) {
      String suspend = properties.getProperty(JVM_DEBUG_SUSPEND, "n");
      optionString.append("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=");
      optionString.append(suspend);
      optionString.append(",address=");
      optionString.append(debugPort);
      optionString.append(" ");
    }

    return optionString.length() > 0 ? optionString.toString() : null;
  }

  public static void main(final String[] args) {
    CommandLineParser commandLineParser = new CommandLineParser(args);
    new WOQContainer(commandLineParser.getParsedProperties(), commandLineParser.getContainerName()).launch();
  }
}
