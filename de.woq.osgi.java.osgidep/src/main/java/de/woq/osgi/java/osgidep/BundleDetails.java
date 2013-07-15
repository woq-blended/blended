/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.woq.osgi.java.osgidep;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;

public class BundleDetails
{
  private BundleContext context;
  private Bundle bundle;
  private PackageAdmin pkgAdmin;
  private BundleFilter filter;

  public BundleDetails(BundleContext context, Bundle bundle, BundleFilter filter, PackageAdmin pkgAdmin)
  {
    this.context = context;
    this.bundle = bundle;
    this.pkgAdmin = pkgAdmin;
    this.filter = filter;
  }

  public Bundle getBundle()
  {
    return bundle;
  }

  public List<ExportedPackage> getExports()
  {
    final List<ExportedPackage> result = new ArrayList<ExportedPackage>();

    if (pkgAdmin.getExportedPackages(getBundle()) != null)
    {
      for (ExportedPackage pkg : pkgAdmin.getExportedPackages(getBundle()))
      {
        final List<Bundle> importers = filter.filterBundles(pkg.getImportingBundles());
        if (importers != null && importers.size() > 0)
        {
          result.add(pkg);
        }
      }
    }
    return result;
  }

  public List<Long> getImporters()
  {
    final List<Long> result = new ArrayList<Long>();
    if (pkgAdmin.getExportedPackages(getBundle()) != null)
    {
      for (ExportedPackage pkg : pkgAdmin.getExportedPackages(getBundle()))
      {
        final List<Bundle> importers = filter.filterBundles(pkg.getImportingBundles());
        if (importers != null)
        {
          for (Bundle importer : importers)
          {
            if (!result.contains(importer.getBundleId()))
            {
              result.add(importer.getBundleId());
            }
          }
        }
      }
    }
    return result;
  }

  public List<ServiceReference> getServiceReferences()
  {
    final List<ServiceReference> result = new ArrayList<ServiceReference>();

    final ServiceReference[] svcRefs = bundle.getRegisteredServices();
    if (svcRefs != null)
    {
      for (ServiceReference ref : svcRefs)
      {
        result.add(ref);
      }
    }
    return result;
  }

  public List<Bundle> getUsingBundles(ServiceReference ref)
  {
    final List<Bundle> result = new ArrayList<Bundle>();

    result.addAll(filter.filterBundles(ref.getUsingBundles()));

    return result;
  }

  public List<Bundle> getUsingBundles()
  {
    final List<Bundle> result = new ArrayList<Bundle>();

    final ServiceReference[] svcRefs = bundle.getRegisteredServices();
    if (svcRefs != null)
    {
      for (ServiceReference ref : svcRefs)
      {
        result.addAll(getUsingBundles(ref));
      }
    }

    return result;
  }

  public BlueprintContainer getBlueprintContainer()
  {
    for (ServiceReference ref : getServiceReferences())
    {
      for (String key : ref.getPropertyKeys())
      {
        if (key.equals("osgi.blueprint.container.version"))
        {
          return (BlueprintContainer) (context.getService(ref));
        }
      }
    }
    return null;
  }
}
