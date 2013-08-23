package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.container.ContainerConstants;
import de.woq.osgi.java.container.WOQContainer;
import org.ops4j.pax.exam.TestAddress;
import org.ops4j.pax.exam.TestContainer;

import java.io.InputStream;
import java.util.Properties;

public class WOQTestContainer implements TestContainer {

  private final String profile;
  private final long delay;

  public WOQTestContainer(final String profile, long delay) {
    this.profile = profile;
    this.delay = delay;
  }

  @Override
  public TestContainer start() {

    Properties props = new Properties();
    props.put(ContainerConstants.PARAM_SYSPROP + ContainerConstants.PROP_WOQ_HOME, "target/test-classes");
    props.put("config.updateInterval", "1000");
    props.put(ContainerConstants.PROP_LOG_LEVEL, "Debug");

    WOQContainer container = new WOQContainer(props, profile);
    container.launch();

    try {
      Thread.sleep(delay);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    return this;
  }

  @Override
  public TestContainer stop() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public long install(InputStream stream) {
    return -1;
  }

  @Override
  public long install(String location, InputStream stream) {
    return -1;
  }

  @Override
  public void call(TestAddress address) {
  }
}
