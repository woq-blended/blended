package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.itestsupport.condition.Condition;
import de.woq.osgi.java.itestsupport.condition.ConditionCanConnect;
import de.woq.osgi.java.itestsupport.condition.ConditionMBeanExists;
import de.woq.osgi.java.itestsupport.condition.ConditionWaiter;
import org.junit.AfterClass;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractWOQITest {

  private static ContainerRunner runner = null;

  @Before
  synchronized public void startContainer() throws Exception {

    if (runner == null) {
      final ContainerProfile profile = getContainerProfile();

      runner = new ContainerRunner(profile.name());
      runner.start();

      ConditionWaiter.waitOnCondition(
        profile.timeout() * 1000l,
        1000l,
        startConditions(runner).toArray(new Condition[]{})
      );
    }
  }

  @Before
  public void waitForConditions() {

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

  private  ContainerProfile getContainerProfile() throws Exception {
    return ProfileResolver.resolveProfile(getClass());
  }

  protected List<Condition> startConditions(final ContainerRunner runner) {

    List<Condition> result = new ArrayList<>();

    result.add(new ConditionCanConnect("localhost", runner.findJMXPort()));
    result.add(new ConditionMBeanExists(runner, "de.woq.osgi.java", "de.woq.osgi.java.container.context.internal.ContainerShutdown"));

    return result;
  }

}
