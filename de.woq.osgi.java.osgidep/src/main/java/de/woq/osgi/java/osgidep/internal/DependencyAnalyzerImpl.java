/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.osgi.java.osgidep.internal;

import java.util.HashMap;
import java.util.Map;

import de.woq.osgi.java.osgidep.BundleDetails;
import de.woq.osgi.java.osgidep.BundleFilter;
import de.woq.osgi.java.osgidep.DependencyAnalyzer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

public class DependencyAnalyzerImpl implements DependencyAnalyzer {

  private BundleContext context = null;
  private PackageAdmin pkgAdmin = null;

  public void setBundleContext(BundleContext context)
  {
    this.context = context;
  }

  public BundleContext getBundleContext()
  {
    return context;
  }

  public PackageAdmin getPkgAdmin()
  {
    if (pkgAdmin == null && getBundleContext() != null)
    {
      ServiceReference ref = getBundleContext().getServiceReference(PackageAdmin.class.getName());
      if (ref != null)
      {
        pkgAdmin = (PackageAdmin) getBundleContext().getService(ref);
      }
    }
    return pkgAdmin;
  }

  public void setPkgAdmin(PackageAdmin pkgAdmin)
  {
    this.pkgAdmin = pkgAdmin;
  }

  @Override
  public Map<Long, BundleDetails> analyze(BundleFilter bf)
  {
    Map<Long, BundleDetails> bundles = new HashMap<Long, BundleDetails>();

    for (Bundle b : getBundleContext().getBundles())
    {
      if (bf.match(b))
      {
        bundles.put(Long.valueOf(b.getBundleId()), new BundleDetails(getBundleContext(), b, bf, getPkgAdmin()));
      }
    }

    return bundles;
  }
}
