package de.woq.osgi.java.mgmt_core;

import java.util.Collection;

public interface OSGIManagementService {

  public Collection<BundleInfo> listBundles();
  public BundleInfo getInfo(final long bundleId);
}
