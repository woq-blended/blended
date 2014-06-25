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

package de.woq.blended.itestsupport.condition;

import de.woq.blended.itestsupport.ContainerConnector;
import de.woq.blended.itestsupport.ContainerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanInfo;
import javax.management.ObjectName;
import java.util.Map;

public class ConditionMBeanExists implements Condition {

  private final ContainerRunner runner;

  private final String   domain;
  private final String   className;
  private final String[] properties;

  private final static Logger LOGGER = LoggerFactory.getLogger(ConditionMBeanExists.class);

  public ConditionMBeanExists(
    final ContainerRunner runner,
    final String domain,
    final String className,
    final String...properties
  ) {
    this.runner = runner;

    this.domain = domain;
    this.className = className;
    this.properties = properties;
  }

  @Override
  public boolean satisfied() {

    boolean result = false;

    ContainerConnector connector = runner.getConnector();

    if (connector != null) {
      try {
        Map<ObjectName, MBeanInfo> infos = connector.getMBeanInfo(new Matcher(domain, className, properties));
        result = infos.size() > 0;
      } catch (Exception e) {
        // ignore
      }
    }

    return result;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + domain + "," + className + "]";
  }

  private class Matcher implements MBeanMatcher {

    private final String domain;
    private final String className;
    private final String[] properties;

    public Matcher(final String domain, final String className, final String...properties) {
      this.domain = domain;
      this.className = className;
      this.properties = properties;
    }

    @Override
    public boolean matchesMBean(ObjectName objectName, MBeanInfo info) {

      if (objectName == null || info == null) {
        return false;
      }

      if (!objectName.toString().startsWith(domain)) {
        return false;
      }

      if (!info.getClassName().equals(className)) {
        return false;
      }


      if(properties != null) {
        for(String prop: properties) {
          if (!objectName.toString().contains(prop)) {
            return false;
          }
        }
      }

      return true;
    }
  }
}
