package de.woq.osgi.java.itestsupport;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractWOQITest {

  private static ContainerRunner runner = null;

  @BeforeClass
  public static void startContainer() throws Exception {
    runner = new ContainerRunner("common");
    runner.start();
  }

  @AfterClass
  public static void stopContainer() throws Exception {
    runner.stop();
    runner.waitForStop();
  }

  protected ContainerRunner getContainerRunner() {
    return runner;
  }
}
