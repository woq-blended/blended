package blended.jmx

import scala.util.Try

import blended.util.logging.Logger
import javax.management.{InstanceNotFoundException, MBeanServer, ObjectName}

trait MBeanRegistrationSupport {

  private[this] val log = Logger[this.type]

  /**
   * The MBeanServer to use to (un-)registration.
   */
  protected def mbeanServer: MBeanServer

  /**
   * Register a MBean.
   * @param mbean The MBean to be registered.
   * @param name The ObjectName that will be used to register the MBean.
   *
   * @param replaceExisting Should another possibly registered MBean with the same name replaced.
   *                        If `false`, this method will result with a [[scala.util.Failure]] containing a [[javax.management.InstanceAlreadyExistsException]]
   */
  def registerMBean(mbean: AnyRef, name: ObjectName, replaceExisting: Boolean = false): Try[Unit] = Try {
    if(mbeanServer.isRegistered(name)) {
      unregisterMBean(name).recover{ case e: InstanceNotFoundException =>
        log.debug(e)(s"Could not unregister existing mbean with name [${name}]")
      }
    }
    mbeanServer.registerMBean(mbean, name)
  }

  /**
   * Unregister a MBean with the given name.
   * @param name The ObjectName of the MBean to unregister.
   */
  def unregisterMBean(name: ObjectName): Try[Unit] = Try {
    mbeanServer.unregisterMBean(name)
  }

}
