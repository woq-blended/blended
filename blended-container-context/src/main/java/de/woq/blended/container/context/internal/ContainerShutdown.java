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

package de.woq.blended.container.context.internal;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.Timer;
import java.util.TimerTask;

public class ContainerShutdown implements ContainerShutdownMBean {

  private BundleContext bundleContext = null;

  private final static long WAIT_MS = 5000l;
  private final Logger LOGGER = LoggerFactory.getLogger(ContainerShutdown.class);

  private MBeanServer mBeanServer = null;

  @Override
  public void shutdown() {
    final Bundle[] bundles = bundleContext.getBundles();

    for(int i = bundles.length - 1; i >= 0; i--) {

      final Bundle b = bundles[i];

      if (bundles[i].getState() == Bundle.ACTIVE) {

        LOGGER.debug("Stopping bundle [" + b.getBundleId() + ":" + b.getSymbolicName() + "]");

        Thread t = new Thread(new Runnable() {
          @Override
          public void run() {

            final long started = System.currentTimeMillis();

            try {
              b.stop();
              while(!(b.getState() == Bundle.RESOLVED) && (System.currentTimeMillis() - started < WAIT_MS));
            } catch (Exception e) {
              LOGGER.error("Failed to stop bundle [" + b.getBundleId() + ":" + b.getSymbolicName() + "]", e);
            }
          }
        });

        t.start();

        try {
          t.join();
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }

    LOGGER.debug("Terminating container JVM...");

    new Timer(true).schedule(new TimerTask() {
      @Override
      public void run() {
        System.exit(0);
      }
    }, 1000l);

  }

  public void init() throws Exception {
    ObjectName objectName = new ObjectName("de.woq.osgi.java:type=ShutdownBean");
    getmBeanServer().registerMBean(this, objectName);
  }

  public BundleContext getBundleContext() {
    return bundleContext;
  }

  public void setBundleContext(BundleContext bundleContext) {
    this.bundleContext = bundleContext;
  }

  public MBeanServer getmBeanServer() {
    return mBeanServer;
  }

  public void setmBeanServer(MBeanServer mBeanServer) {
    this.mBeanServer = mBeanServer;
  }
}
