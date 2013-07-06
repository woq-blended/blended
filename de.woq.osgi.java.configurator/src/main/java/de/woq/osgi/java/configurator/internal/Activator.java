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

import java.util.Timer;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator
{

  private final static Logger LOG = LoggerFactory.getLogger(Activator.class);

  private final static String PROP_UPDATE_INTERVAL = "config.updateInterval";
  private final static String DEFAULT_INTERVAL = "10000";

  private Timer configUpdateTask = new Timer();

  public void start(final BundleContext bundleContext) throws Exception
  {
    long interval = new Long(DEFAULT_INTERVAL).longValue();

    try
    {
      String stringInterval = System.getProperty(PROP_UPDATE_INTERVAL, DEFAULT_INTERVAL);
      interval = new Long(interval).longValue();
    }
    catch (Exception nfe)
    {
      LOG.warn("Unable to read config update interval from config.");
    }

    configUpdateTask.schedule(new ConfigUpdater(bundleContext), 0, interval);
  }

  public void stop(final BundleContext bundleContext) throws Exception
  {
    configUpdateTask.cancel();
  }

}
