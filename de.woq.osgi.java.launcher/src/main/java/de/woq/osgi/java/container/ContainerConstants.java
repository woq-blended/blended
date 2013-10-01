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

public interface ContainerConstants {
  String CONTAINER_CFG_FILE = "container.properties";

  String PROP_PLATFORM_VENDOR = "platform.vendor";

  String DEFAULT_PLATFORM_VENDOR = "equinox";

  String PROP_PLATFORM_VERSION = "platform.version";

  String DEFAULT_PLATFORM_VERSION = "3.8.1";

  String PROP_PLATFORM_STARTLEVEL = "platform.startLevel";

  String DEFAULT_PLATFORM_STARTLEVEL = "100";

  String PROP_PROXY_HOST = "proxy.host";

  String PROP_PROXY_PORT = "proxy.port";

  String DEFAULT_PROXY_PORT = "8080";

  String PROP_PROXY_USER = "proxy.user";

  String PROP_PROXY_PASSWORD = "proxy.password";

  String PROP_LOG_LEVEL = "log.level";

  String DEFAULT_LOG_LEVEL = "info";

  String JVM_DEBUG_PORT = "jvm.debug.port";

  String JVM_DEBUG_SUSPEND = "jvm.debug.suspend";

  String JVM_STACK_SIZE = "jvm.stacksize";

  String DEFAULT_JVM_STACK_SIZE = "256k";

  String JVM_MIN_HEAP = "jvm.minHeap";

  String DEFAULT_JVM_MIN_HEAP = "256m";

  String JVM_MAX_HEAP = "jvm.maxHeap";

  String DEFAULT_JVM_MAX_HEAP = "512m";

  String PARAM_SYSPROP = "jvm.property.";

  String PROP_WOQ_HOME = PARAM_SYSPROP + "woq.home";
}
