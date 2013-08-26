package de.woq.osgi.java.itestsupport;

import org.junit.AfterClass;
import org.junit.Before;

public abstract class AbstractWOQITest {

  private static ContainerRunner runner = null;

  @Before
  synchronized public void startContainer() throws Exception {

    if (runner == null) {
      runner = new ContainerRunner(getProfileName());
      runner.start();
    }
  }

  @AfterClass
  synchronized public static void stopContainer() throws Exception {
    if (runner != null) {
      runner.stop();
      runner.waitForStop();
    }
  }

  protected ContainerRunner getContainerRunner() {
    return runner;
  }

  private  String getProfileName() throws Exception {
    return ProfileResolver.resolveProfile(getClass()).name();
  }

}
