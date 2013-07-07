package de.woq.osgi.java.mgmt_core.internal;

import java.util.ArrayList;
import java.util.Collection;

import de.woq.osgi.java.mgmt_core.BundleInfo;
import de.woq.osgi.java.mgmt_core.OSGIManagementService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class OSGIManagementServiceImpl implements OSGIManagementService {

  private final BundleContext bundleContext;


  public OSGIManagementServiceImpl(BundleContext bundleContext) {
    this.bundleContext = bundleContext;
  }

  @Override
  public Collection<BundleInfo> listBundles() {

    Collection<BundleInfo> result = new ArrayList<BundleInfo>();

    for(Bundle b : bundleContext.getBundles()) {
      result.add(createBundleInfo(b));
    }

    return result;
  }

  @Override
  public BundleInfo getInfo(long bundleId) {
    Bundle b = bundleContext.getBundle(bundleId);
    return b == null ? null : createBundleInfo(b);
  }

  private BundleInfo createBundleInfo(Bundle bundle) {
    BundleInfo info = new BundleInfo();
    info.setBundleId(bundle.getBundleId());
    info.setSymbolicName(bundle.getSymbolicName());
//      String packages = b.getHeaders().get("Export-Package");
//      if (packages != null) {
//        info.setExportPackages(packages.split(",'"));
//      }
    return info;
  }
}
