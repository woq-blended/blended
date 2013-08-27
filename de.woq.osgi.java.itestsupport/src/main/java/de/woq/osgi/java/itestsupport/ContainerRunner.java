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

package de.woq.osgi.java.itestsupport;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContainerRunner {

  public final static String OBJ_NAME_SHUTDOWN = "de.woq.osgi.java:type=ShutdownBean";

  private final String profile;
  private final CountDownLatch latch = new CountDownLatch(1);
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Executor executor;
  private final ExecuteWatchdog watchdog;

  private final static String PROP_CONTAINER_CMD    = "woq.container.command";
  private final static String DEFAULT_CONTAINER_CMD = "woqContainer";

  private ContainerConnector connector = null;

  public ContainerRunner(String profile) throws Exception {
    this.profile = profile;

    executor = new DefaultExecutor();
    executor.setStreamHandler(new PumpStreamHandler(new ContainerOutputStream(), System.err));
    watchdog = new ExecuteWatchdog(-1);

    executor.setWatchdog(watchdog);
  }

  public synchronized void start() throws Exception {

    started.set(true);

    Runnable runner = new Runnable() {
      @Override
      public void run() {
        try {
          CommandLine cl = new CommandLine(findContainerDirectory() + "/bin/" + getContainerCommand()).addArguments(profile);
          executor.execute(cl, new ExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
              latch.countDown();
            }

            @Override
            public void onProcessFailed(ExecuteException e) {
              latch.countDown();
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };

    new Thread(runner).start();
  }

  public synchronized void stop() throws Exception {
    System.out.println("Stop container ...") ;
    getConnector().invoke("de.woq.osgi.java:type=ShutdownBean", "shutdown");
  }

  public void waitForStop() {
    if (latch.getCount() > 0) {
      try {
        latch.await();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized ContainerConnector getConnector() {
    if (connector == null) {
      try {
        connector = new ContainerConnector("localhost", findJMXPort());
      } catch (Exception e) {
        // ignore
      }
    }
    return connector;
  }

  public String findContainerDirectory() throws Exception {
    File dir = new File("./target/container");

    if (!dir.exists()) {
      throw new Exception("Directory [" + dir.getAbsolutePath() + "] does not exist.");
    }

    String[] subDirs = dir.list();

    if (subDirs.length != 1) {
      throw new Exception("Expected only one subdirectory in [" + dir.getAbsolutePath() + "]");
    }

    return new File(dir, subDirs[0]).getAbsolutePath();
  }

  public int findJMXPort() {
    Properties props = new Properties();

    try {
      File propFile = new File(findContainerDirectory() + "/config", profile + ".container.properties");

      if (propFile.exists() && propFile.isFile() && propFile.canRead()) {
        InputStream is = null;
        is = new FileInputStream(propFile);
        props.load(is);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    String sPort = props.getProperty("jvm.property.com.sun.management.jmxremote.port", "1099");

    return Integer.parseInt(sPort);
  }

  private String getContainerCommand() {

    String result = System.getProperty(PROP_CONTAINER_CMD, DEFAULT_CONTAINER_CMD);

    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
      result += ".bat";
    } else {
      result += ".sh";
    }

    return result;
  }
}
