package blended.akka.http.jmsqueue.internal

import domino.service_consuming.ServiceConsuming
import javax.jms.ConnectionFactory
import org.osgi.framework.BundleContext

class OsgiHttpQueueService(
  override val qConfig : HttpQueueConfig,
  override val bundleContext : BundleContext
) extends HttpQueueService  with ServiceConsuming {
  override def withConnectionFactory[T](vendor: String, provider: String)(f: Option[ConnectionFactory] => T): T = {
    withAdvancedService[ConnectionFactory, T](s"&(vendor=$vendor)(provider=$provider)") { ocf =>
      f(ocf)
    }
  }
}
