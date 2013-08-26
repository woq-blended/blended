package de.woq.osgi.java.itestsupport.condition;

import javax.management.MBeanInfo;
import javax.management.ObjectName;

public interface MBeanMatcher {

  public boolean matchesMBean(final ObjectName objectName, final MBeanInfo info);
}
