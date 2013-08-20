package de.woq.osgi.java.itestsupport;

public abstract class AbstractWOQITest {

  private static WOQTestContainer container;

  synchronized protected WOQTestContainer getContainer() throws Exception {

    WithComposite compositeSpec = getCompositeSpec();

    if (container == null) {
      container = new WOQTestContainer(compositeSpec.location(), compositeSpec.delay());
    }
    return container;
  }

  protected WithComposite getCompositeSpec() throws Exception {

    WithComposite compositeSpec = getClass().getAnnotation(WithComposite.class);
    if (compositeSpec == null) {
      throw new Exception("No Annotation 'WithComposite' set on class [{" + getClass().getName() + "}].");
    }
    return compositeSpec;
  }
}
