package de.woq.blended.itestsupport.jmx

import javax.management.{MBeanInfo, ObjectName}

trait MBeanMatcher {
  def matchesMBean(name: ObjectName, info: MBeanInfo)
}
