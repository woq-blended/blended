package blended.testsupport.pojosr

import java.net.URL
import java.util
import java.util.Collections

import org.apache.felix.connect.felix.framework.ServiceRegistry
import org.apache.felix.connect.felix.framework.util.EventDispatcher
import org.apache.felix.connect.launch.BundleDescriptor
import org.apache.felix.connect.{BlendedPojoBundle, PojoSR, Revision}
import org.osgi.framework.{Bundle, BundleActivator, Version}

import scala.collection.JavaConverters._

class BlendedPojoRegistry(config : Map[String, Any]) extends PojoSR(config.asJava) {

  import org.osgi.framework.Constants._

  def startBundle(
    symbolicName : String,
    activator : BundleActivator
  ) : Long = {

    val url = s"file://$symbolicName"

    val revision = new Revision {
      override def getLastModified : Long = System.currentTimeMillis()

      override def getEntries : util.Enumeration[String] = Collections.emptyEnumeration()

      override def getEntry(entryName : String) : URL =
        getClass().getClassLoader().getResource(entryName)
    }

    val descriptor = new BundleDescriptor(
      getClass().getClassLoader(),
      url,
      Map(BUNDLE_SYMBOLICNAME -> symbolicName).asJava
    )

    val version = Version.emptyVersion

    val bundles = getField[java.util.Map[java.lang.Long, Bundle]]("m_bundles")

    val id = bundles.size()

    val bundle = new BlendedPojoBundle(
      activator = activator,
      registry = getField[ServiceRegistry]("m_registry"),
      dispatcher = getField[EventDispatcher]("m_dispatcher"),
      bundles = bundles,
      location = descriptor.getUrl(),
      id = id,
      symbolicName = symbolicName,
      version = version,
      revision = revision,
      headers = descriptor.getHeaders(),
      config = new util.HashMap[String, Object]()
    )

    bundles.put(bundles.size().toLong, bundle)
    bundle.start()

    id
  }

  def serviceRegistry() : ServiceRegistry = {
    val field = classOf[PojoSR].getDeclaredField("m_registry")
    field.setAccessible(true)
    field.get(this).asInstanceOf[ServiceRegistry]
  }

  private[this] def getField[T](name : String) : T = {
    val field = classOf[PojoSR].getDeclaredField(name)
    field.setAccessible(true)
    field.get(this).asInstanceOf[T]
  }
}
