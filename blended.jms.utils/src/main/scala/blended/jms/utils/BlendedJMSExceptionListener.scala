package blended.jms.utils

import akka.actor.ActorSystem
import blended.jms.utils.internal.ConnectionException
import domino.service_consuming.ServiceConsuming
import javax.jms.{ExceptionListener, JMSException}
import org.osgi.framework.BundleContext

class BlendedJMSExceptionListener(override val bundleContext: BundleContext, vendor: String, provider: String)
  extends ExceptionListener
  with ServiceConsuming {

  private[this] val log = org.log4s.getLogger

  override def onException(e: JMSException): Unit = {
    log.error(e)("Encountered JMS Exception")
    withService[ActorSystem, Unit] {
      case None => // do nothing
      case Some(s) => s.eventStream.publish(ConnectionException(vendor,provider,e))
    }
  }
}
