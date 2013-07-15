package de.woq.osgi.java.container.context.internal;

import java.io.File;

import de.woq.osgi.java.container.context.ContainerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerContextImpl implements ContainerContext {

  private final static String PROP_WOQ_HOME = "woq.home";

  private final static String CONFIG_DIR = "config";

  private final static Logger LOGGER = LoggerFactory.getLogger(ContainerContextImpl.class);

  @Override
  public String getContainerDirectory() {

    String dir = System.getProperty(PROP_WOQ_HOME);

    if (dir == null) {
      dir = System.getProperty("user.dir");
    }

    File configDir = new File(dir);

    if (!configDir.exists()) {
      LOGGER.error("Directory [" + dir + "] does not exist.");
      configDir = null;
    }

    if (configDir != null && (!configDir.isDirectory() || !configDir.canRead()))
    {
      LOGGER.error("Directory [" + dir + "] is not readable.");
      configDir = null;
    }

    return configDir.getAbsolutePath();
  }

  @Override
  public String getContainerConfigDirectory() {
    return getContainerDirectory() + "/" + CONFIG_DIR;
  }
}
