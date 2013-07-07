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
