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
package de.woq.osgi.java.configurator.internal;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigUpdater extends TimerTask
{

  private final static String LOG_CONFIG_ID = "org.ops4j.pax.logging";
  private final static String PROP_FILE_EXT = ".cfg";
  private final static String PROP_WOQ_HOME = "woq.home";
  private final static String LOG_PREFIX = "log4j.";

  private final BundleContext bundleContext;

  private File configDir = null;
  private AtomicBoolean running = new AtomicBoolean(false);
  private final Map<String, Long> lastUpdatedMap = new HashMap<String, Long>();

  public ConfigUpdater(final BundleContext context)
  {
    this.bundleContext = context;
  }

  private File getConfigDir()  {
    Logger log = getLogger();

    if (configDir == null)
    {
      String dir = System.getProperty(PROP_WOQ_HOME);
      if (dir != null)
      {
        dir = dir + "/config";
      }
      else
      {
        dir = System.getProperty("user.dir");
      }

      log.info("WOQ Activator using directory [" + dir + "]");

      configDir = new File(dir);
      if (!configDir.exists()) {
        log.error("Directory [" + dir + "] does not exist.");
        configDir = null;
      }

      if (configDir != null && (!configDir.isDirectory() || !configDir.canRead()))
      {
        log.error("Directory [" + dir + "] is not readable.");
        configDir = null;
      }
    }

    return configDir;
  }

  private void updateConfig(final String configId, final String fileName)
  {
    Logger log = getLogger();

    if (getConfigDir() != null)
    {
      File propFile = new File(getConfigDir(), fileName);
      if (!propFile.exists() || !propFile.canRead())
      {
        System.err.println("Could not read [" + propFile.getAbsolutePath() + "].");
        return;
      }
      else
      {
        long lastUpdated = lastUpdatedMap.get(configId) == null ? Long.MIN_VALUE : lastUpdatedMap.get(configId);

        if (propFile.lastModified() > lastUpdated)
        {
          log.info("Updating [" + configId + "] from [" + fileName + "]");
          InputStream is = null;
          try
          {
            Properties logProps = new Properties();
            is = new FileInputStream(propFile);
            logProps.load(is);
            updateConfiguration(bundleContext, configId, logProps);
            lastUpdatedMap.put(configId, propFile.lastModified());
          }
          catch (Exception e)
          {
            System.err.println("Error updating logger configuration.");
          }
          finally
          {
            if (is != null)
            {
              try {
                is.close();
              }
              catch (Exception e) {}
            }
          }
        }
        else
        {
          log.debug("Nothing to update for [" + configId + "]");
        }
      }
    }
  }

  @Override
  public void run()
  {
    Logger log = getLogger();

    if (running.get())
    {
      log.debug("Skipping config update, already executing.");
      return;
    }
    running.set(true);

    log.debug("Checking for config update");


    if (getConfigDir() != null)
    {
      updateConfig(LOG_CONFIG_ID, "log4j.properties");

      File[] files = getConfigDir().listFiles(new FileFilter()
      {
        @Override
        public boolean accept(File file)
        {
          return file.isFile() && file.canRead() && file.getName().endsWith(PROP_FILE_EXT);
        }
      });

      log.debug("Found [" + files.length + "] property configuration files.");

      for(File configFile: files)
      {
        String configId = configFile.getName();
        configId = configId.substring(0, configId.length() - PROP_FILE_EXT.length());
        updateConfig(configId, configFile.getName());
      }
    }

    running.set(false);
  }

  private void updateConfiguration(
   final BundleContext bundleContext,
   final String id, final Properties
   props) throws Exception
  {
    final ServiceReference ref = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());

    if (ref == null)
    {
      throw new IllegalStateException("Cannot find a configuration admin service");
    }

    ConfigurationAdmin cfgAdmin = (ConfigurationAdmin) bundleContext.getService(ref);

    try
    {
      final Configuration configuration = cfgAdmin.getConfiguration(id, null);
      configuration.update((Dictionary)props);
    }
    catch (Exception e)
    {
      System.err.println("Error updating [" + id + "]\n" + e.getMessage());
    }
    finally
    {
      bundleContext.ungetService(ref);
    }
  }

  private Logger getLogger()
  {
    return LoggerFactory.getLogger(getClass());
  }
}
