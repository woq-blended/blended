package de.woq.osgi.java.container.id;

import java.util.Properties;

/**
 * Each container within the infrastructure has a unique ID. Once the unique ID is assigned to
 * a container, it doesn't change and also survives container restarts.
 * A set of user defined properties can be associated with the container id. This can be used
 * within the registration process at the datacenter and also to provide a simple mechanism for
 * container meta data.
 */
public interface ContainerIdentifierService {

  public String getUUID();

  public Properties getProperties();
}
