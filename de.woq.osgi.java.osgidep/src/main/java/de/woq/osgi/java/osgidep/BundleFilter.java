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

package de.woq.osgi.java.osgidep;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;

public class BundleFilter
{
  private List<String> filter = null;

  public BundleFilter()
  {
  }

  public BundleFilter(List<String> filter)
  {
    this.filter = filter;
  }

  public BundleFilter(String... filter)
  {
    this.filter = new ArrayList<String>();

    for (String s : filter)
    {
      this.filter.add(s);
    }
  }

  public boolean match(Bundle b)
  {
    if (filter == null || filter.size() == 0)
    {
      return true;
    }

    for (String f : filter)
    {
      if (b.getSymbolicName().matches(f))
      {
        return true;
      }
    }

    return false;
  }

  public List<Bundle> filterBundles(Bundle[] bundles)
  {
    final List<Bundle> result = new ArrayList<Bundle>();

    if (bundles != null)
    {
      for (Bundle b : bundles)
      {
        if (match(b))
        {
          result.add(b);
        }
      }
    }
    return result;
  }

}
