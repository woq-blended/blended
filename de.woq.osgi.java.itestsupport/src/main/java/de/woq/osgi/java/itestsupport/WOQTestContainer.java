package de.woq.osgi.java.itestsupport;

import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.spi.PaxExamRuntime;

import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

public class WOQTestContainer {

  private final String composite;
  private final long   delay;

  private TestContainer container = null;

  public WOQTestContainer(String composite, long delay) {
    this.composite = composite;
    this.delay = delay;
  }

  public synchronized void start() throws Exception {

    if (container == null) {
      final ExamSystem examSystem = PaxExamRuntime.createServerSystem(containerConfiguration());
      container = PaxExamRuntime.createContainer(examSystem);
      container.start();
      if (delay > 0) {
        Thread.sleep(delay);
      }
    }
  }

  public synchronized void stop() throws Exception {
    if (container != null) {
      if (delay > 0) {
        Thread.sleep(delay);
      }
      container.stop();
      container = null;
    }
  }

  protected Option[] containerConfiguration() throws Exception {
    return options(
      new CompositeBundleListProvider(composite).getBundles(),
      systemProperty("config.updateInterval").value("1000"),
      systemProperty("woq.home").value("target/test-classes"),
      systemProperty("osgi.startlevel.framework").value("100")
    );
  }
}
