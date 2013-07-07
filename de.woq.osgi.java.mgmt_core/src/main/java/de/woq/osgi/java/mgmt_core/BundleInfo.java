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

package de.woq.osgi.java.mgmt_core;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class BundleInfo {

  private Long     bundleId        = Long.valueOf(0);
  private String   symbolicName    = "";
  private String[] exportPackages  = new String[] {};

  public Long getBundleId() {
    return bundleId;
  }

  public void setBundleId(Long bundleId) {
    this.bundleId = bundleId;
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  public void setSymbolicName(String symbolicName) {
    this.symbolicName = symbolicName;
  }

  public String[] getExportPackages() {
    return exportPackages;
  }

  public void setExportPackages(String[] exportPackage) {
    this.exportPackages = exportPackage;
  }
}
