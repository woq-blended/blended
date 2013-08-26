package de.woq.osgi.java.itestsupport.condition;

import de.woq.osgi.java.itestsupport.ContainerRunner;

public class ConditionCamelContextExists extends ConditionMBeanExists {

  public ConditionCamelContextExists(final ContainerRunner runner, final String contextName) {
    super(runner, "org.apache.camel", "org.apache.camel.management.mbean.ManagedCamelContext",  "name=\"" + contextName + "\"");
  }
}
