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

package de.wayofquality.blended.container.context.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.wayofquality.blended.container.context.ContainerContext;

public class ContainerContextImpl implements ContainerContext {

  private final static String PROP_WOQ_HOME = "woq.home";
  private final static String CONFIG_DIR = "etc";

  private final Logger log = LoggerFactory.getLogger(ContainerContextImpl.class);

  @Override
  public String getContainerHostname() {

    String result = "UNKNOWN";

    try {
      final InetAddress localMachine = java.net.InetAddress.getLocalHost();
      result = localMachine.getCanonicalHostName();
    } catch (final java.net.UnknownHostException uhe) {
      // ignore
    }
    return result;
  }

  @Override
  public String getContainerDirectory() {

    String dir = System.getProperty(PROP_WOQ_HOME);

    if (dir == null) {
      dir = System.getProperty("user.dir");
    }

    File configDir = new File(dir);

    if (!configDir.exists()) {
      log.error("Directory [{}] does not exist.", dir);
      configDir = null;
    }

    if (configDir != null && (!configDir.isDirectory() || !configDir.canRead())) {
      log.error("Directory [{}] is not readable.", dir);
      configDir = null;
    }

    return configDir.getAbsolutePath();
  }

  @Override
  public String getContainerConfigDirectory() {
    return getContainerDirectory() + "/" + CONFIG_DIR;
  }

  @Override
  public Properties readConfig(final String configId) {

    final Properties props = new Properties();

    final File f = new File(getConfigFile(configId));

    if (!f.exists() || f.isDirectory() || !f.canRead()) {
      log.warn("Cannot open [{}]", f.getAbsolutePath());
      return props;
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      props.load(is);
    } catch (final Exception e) {
      log.warn("Error reading config file.", e);
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (final Exception e) {
        }
      }
    }

    log.info("Read [{}] properties from [{}]", props.size(), f.getAbsolutePath());
    return props;
  }

  @Override
  public void writeConfig(final String configId, final Properties props) {

    final String configFile = getConfigFile(configId);

    log.debug("Wrting config for [{}] to [{}].", configId, configFile);

    OutputStream os = null;
    try {
      os = new FileOutputStream(configFile);
      props.store(os, "");
    } catch (final Exception e) {
      log.warn("Error writing config file.", e);
    } finally {
      try {
        if (os != null) {
          os.close();
        }
      } catch (final Exception e) {
      }
    }

    log.info("Exported configuration [{}]", configFile);
  }

  private String getConfigFile(final String configId) {
    return getContainerConfigDirectory() + "/" + configId + ".cfg";
  }
}
