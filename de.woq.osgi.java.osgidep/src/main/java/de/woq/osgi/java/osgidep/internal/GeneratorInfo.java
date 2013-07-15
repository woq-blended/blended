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

package de.woq.osgi.java.osgidep.internal;

import java.util.ArrayList;
import java.util.List;

public class GeneratorInfo
{
  private String templateName;
  private List<String> filters = new ArrayList<String>();
  private String targetName = null;

  public String getTemplateName()
  {
    return templateName;
  }

  public void setTemplateName(String templateName)
  {
    this.templateName = templateName;
  }

  public List<String> getFilters()
  {
    return filters;
  }

  public void setFilters(List<String> filters)
  {
    this.filters = filters;
  }

  public String getTargetName()
  {
    return targetName == null ? getTemplateName() : targetName;
  }

  public void setTargetName(String targetName)
  {
    this.targetName = targetName;
  }

}
