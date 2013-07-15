package de.woq.osgi.java.container.context;

import java.util.Properties;

public interface ContainerContext {

  public String getContainerDirectory();
  public String getContainerConfigDirectory();

  public Properties readConfig(final String configId);
  public void writeConfig(final String configId, final Properties props);
}
