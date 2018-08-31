package blended.akka.http.jmsqueue.internal

import domino.service_consuming.ServiceConsuming
import javax.jms.ConnectionFactory
import org.osgi.framework.BundleContext

import scala.concurrent.ExecutionContext

class OsgiHttpQueueService(
  override val qConfig : HttpQueueConfig,
  override val bundleContext : BundleContext,
  override implicit val eCtxt: ExecutionContext
) extends HttpQueueService  with ServiceConsuming {
  override def withConnectionFactory[T](vendor: String, provider: String)(f: Option[ConnectionFactory] => T): T = {
    withAdvancedService[ConnectionFactory, T](s"(&(vendor=$vendor)(provider=$provider))") { f(_) }
  }
}
