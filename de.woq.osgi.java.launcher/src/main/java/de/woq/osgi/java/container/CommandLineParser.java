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

public class CommandLineParser {

  private Properties commandLineProperties = new Properties();

  private Properties parsedProperties = null;
  private List<String> parsedArguments = new ArrayList<>();
  private String containerName = "empty";

  public CommandLineParser(final String... args) {

    parseArguments(args);

    if (parsedArguments.size() > 0) {
      containerName = parsedArguments.get(0);
      parsedArguments.remove(0);
    }

    parseProperties(args);

  }

  public String getContainerName() {
    return containerName;
  }

  public Properties getParsedProperties() {
    return parsedProperties;
  }

  public List<String> getParsedArguments() {
    return parsedArguments;
  }

  private void parseProperties(final String...args) {
    if (parsedProperties == null) {
      parsedProperties = new Properties();
      try {
        System.out.println("Loading [" + getContainerName() + "." + ContainerConstants.CONTAINER_CFG_FILE + "]");
        parsedProperties.load(
          WOQContainer.class.getResourceAsStream("/" + getContainerName() + "." + ContainerConstants.CONTAINER_CFG_FILE)
        );
      } catch (Exception e) {
        try {
          System.out.println("Falling back to [" + ContainerConstants.CONTAINER_CFG_FILE + "]");
          parsedProperties.load(WOQContainer.class.getResourceAsStream("/" + ContainerConstants.CONTAINER_CFG_FILE));
        } catch (Exception inner) {
          System.out.println("Unable to load container properties file.");
        }
      }

      for(String key: commandLineProperties.stringPropertyNames()) {
        if (!parsedProperties.containsKey(key)) {
          parsedProperties.put(key, commandLineProperties.getProperty(key));
        }
      }
    }
  }

  private void parseArguments(final String...args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-" + ContainerConstants.PARAM_SYSPROP)) {
        String propName = args[i];
        i++;
        String propValue = i < args.length ? args[i] : "";
        commandLineProperties.setProperty(propName.substring(1), propValue);
      } else {
        parsedArguments.add(args[i]);
      }
    }
  }
}
