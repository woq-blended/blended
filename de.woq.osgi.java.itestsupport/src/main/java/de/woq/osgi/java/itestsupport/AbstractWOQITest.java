package de.woq.osgi.java.itestsupport;

import org.junit.After;
import org.junit.Before;

public abstract class AbstractWOQITest {

  private static WOQTestContainer container;

  @Before
  public void startServer() throws Exception {
    WithComposite compositeSpec = getCompositeSpec();

    container = new WOQTestContainer(compositeSpec.location(), compositeSpec.delay());
    container.start();
  }

  @After
  public void stopContainer() throws Exception {
    container.stop();
  }

  protected WithComposite getCompositeSpec() throws Exception {

    WithComposite compositeSpec = getClass().getAnnotation(WithComposite.class);
    if (compositeSpec == null) {
      throw new Exception("No Annotation 'WithComposite' set on class [{" + getClass().getName() + "}].");
    }
    return compositeSpec;
  }
}
