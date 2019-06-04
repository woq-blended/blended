package org.apache.felix.connect

import java.util

import org.apache.felix.connect.felix.framework.ServiceRegistry
import org.apache.felix.connect.felix.framework.util.EventDispatcher
import org.osgi.framework._

import scala.collection.JavaConverters._

class BlendedPojoBundle(
  activator : BundleActivator,
  registry : ServiceRegistry,
  dispatcher : EventDispatcher,
  bundles : java.util.Map[java.lang.Long, Bundle],
  location : String,
  id : Long,
  symbolicName : String,
  version : Version,
  revision : Revision,
  headers : java.util.Map[String, String],
  config : util.HashMap[String, Object]
) extends PojoSRBundle(
  registry,
  dispatcher,
  bundles,
  location,
  id,
  symbolicName,
  version,
  revision,
  getClass().getClassLoader(),
  headers,
  new util.HashMap[Class[_], Object](),
  config
) {

  @throws[BundleException]
  override def start() : Unit = {

    if (m_state != Bundle.RESOLVED) {
      if (m_state == Bundle.ACTIVE) return
      throw new BundleException("Bundle is in wrong state for start")
    }

    try {
      m_state = Bundle.STARTING

      m_context = new PojoSRBundleContext(this, registry, dispatcher, bundles, config)
      dispatcher.fireBundleEvent(new BundleEvent(BundleEvent.STARTING, this))

      activator.start(m_context)
      m_state = Bundle.ACTIVE
      dispatcher.fireBundleEvent(new BundleEvent(BundleEvent.STARTED, this))
    } catch {
      case t : Throwable =>
        m_state = Bundle.RESOLVED
        dispatcher.fireBundleEvent(new BundleEvent(BundleEvent.STOPPED, this))
        throw new BundleException("Unable to start bundle", t)
    }
  }

  @throws[BundleException]
  override def stop() {

    m_state match {
      case Bundle.ACTIVE =>
        try {
          m_state = Bundle.STOPPING
          dispatcher.fireBundleEvent(new BundleEvent(BundleEvent.STOPPING, this))
          activator.stop(m_context)
        } catch {
          case ex : Throwable => throw new BundleException("Error while stopping bundle", ex);
        } finally {
          registry.unregisterServices(this);
          dispatcher.removeListeners(m_context);
          m_context = null;
          m_state = Bundle.RESOLVED;
          dispatcher.fireBundleEvent(new BundleEvent(BundleEvent.STOPPED, this));
        }

      case Bundle.RESOLVED =>

      case _               => throw new BundleException("Bundle is in wrong state for stop")
    }
  }
}
