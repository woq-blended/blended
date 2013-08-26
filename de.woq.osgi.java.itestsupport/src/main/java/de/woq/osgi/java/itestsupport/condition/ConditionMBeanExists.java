package de.woq.osgi.java.itestsupport.condition;

import de.woq.osgi.java.itestsupport.ContainerConnector;
import de.woq.osgi.java.itestsupport.ContainerRunner;

import javax.management.MBeanInfo;

public class ConditionMBeanExists implements Condition {

  private final ContainerRunner runner;
  private final String objectName;

  public ConditionMBeanExists(final ContainerRunner runner, final String objectName) {
    this.runner = runner;
    this.objectName = objectName;
  }

  @Override
  public boolean satisfied() {

    boolean result = false;

    ContainerConnector connector = runner.getConnector();

    if (connector != null) {
      try {
        connector.connect();
        MBeanInfo info = connector.getMBeanInfo(objectName);
        result = info != null;
      } catch (Exception e) {
        // ignore
      }
    }

    return result;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + objectName + "]";
  }
}
