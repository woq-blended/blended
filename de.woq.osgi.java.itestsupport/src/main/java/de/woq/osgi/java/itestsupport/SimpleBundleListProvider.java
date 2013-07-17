package de.woq.osgi.java.itestsupport;

import org.ops4j.pax.exam.options.CompositeOption;
import org.ops4j.pax.exam.options.DefaultCompositeOption;

import static org.ops4j.pax.exam.CoreOptions.*;

public class SimpleBundleListProvider implements BundleListProvider {

  @Override
  public CompositeOption getBundles() {

    return new DefaultCompositeOption(
      options(
        mavenBundle().
          groupId("net.sourceforge.cglib").artifactId("com.springsource.net.sf.cglib").version("2.2.0").startLevel(2)
      )
    );
  }
}
