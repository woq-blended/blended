package blended.jmx

import javax.management.DynamicMBean

/**
 * Maps ordinary Scala classes to Open (Dynamic) MBeans.
 */
trait OpenMBeanMapper {

  /**
   * Maps a product (case class) to a Open MBean.
   * @param cc The case class.
   * @return The Open MBean.
   */
  def mapProduct(cc: Product): DynamicMBean

}
