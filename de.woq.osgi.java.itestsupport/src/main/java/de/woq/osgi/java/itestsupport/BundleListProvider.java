package de.woq.osgi.java.itestsupport;

import org.ops4j.pax.exam.options.CompositeOption;

public interface BundleListProvider {

  public CompositeOption getBundles() throws Exception;
}
