package de.woq.osgi.java.container.context.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

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

  @Override
  public Properties readConfig(String configId) {

    Properties props = new Properties();

    File f = new File(getConfigFile(configId));

    if (!f.exists() || f.isDirectory() || !f.canRead()) {
      LOGGER.warn("Cannot open [" + f.getAbsolutePath() + "]");
      return props;
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      props.load(is);
    } catch(Exception e) {
      LOGGER.warn("Error reading config file.", e);
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {}
      }
    }

    LOGGER.info(String.format("Read [%d] properties from [%s]", props.size(), f.getAbsolutePath()));
    return props;
  }

  @Override
  public void writeConfig(String configId, Properties props) {

    OutputStream os = null;
    try {
      os = new FileOutputStream(getConfigFile(configId));
      props.store(os, "");
    } catch (Exception e) {
      LOGGER.warn("Error writing config file.", e);
    } finally {
      try {
        os.close();
      } catch (Exception e) {}
    }
  }

  private String getConfigFile(final String configId) {
    return getContainerConfigDirectory() + "/" + configId + ".cfg";
  }
}
