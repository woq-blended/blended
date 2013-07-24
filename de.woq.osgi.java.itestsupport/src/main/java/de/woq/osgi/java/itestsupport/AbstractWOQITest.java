package de.woq.osgi.java.itestsupport;

import org.junit.Rule;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExamServer;

import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

public abstract class AbstractWOQITest {

  @Rule
  public PaxExamServer exam = new PaxExamServer();

  @Configuration
  public Option[] config() throws Exception {
    return containerConfiguration();
  }

  protected Option[] containerConfiguration() throws Exception {

    return options(
      new CompositeBundleListProvider(getCompositeLocation()).getBundles(),
      systemProperty("config.updateInterval").value("1000"),
      systemProperty("woq.home").value("target/test-classes"),
      frameworkStartLevel(100)
    );
  }

  private String getCompositeLocation() throws Exception {
    WithComposite compositeSpec = getClass().getAnnotation(WithComposite.class);
    if (compositeSpec == null) {
      throw new Exception("No Annotation 'WithComposite' set on class [" + getClass().getName() + "]");
    }
    return compositeSpec.location();
  }
}
