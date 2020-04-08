package blended.itestsupport.jms

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor._
import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import blended.jms.utils._
import blended.util.logging.Logger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object JMSAvailableCondition {
  def apply(cf: IdAwareConnectionFactory, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) =
    AsyncCondition(Props(JMSChecker(cf)), s"JMSAvailableCondition($cf)", t)
}

private[jms] object JMSChecker {
  def apply(cf: IdAwareConnectionFactory) = new JMSChecker(cf)
}

private[jms] class JMSChecker(cf: IdAwareConnectionFactory) extends AsyncChecker {

  private val log : Logger = Logger[JMSChecker]
  var connected: AtomicBoolean = new AtomicBoolean(false)
  var connecting: AtomicBoolean = new AtomicBoolean(false)

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def performCheck(cond: AsyncCondition): Future[Boolean] = {

    log.debug(s"Checking JMS connection...[$cf]")

    if ((!connected.get()) && (!connecting.get())) {
      connecting.set(true)

      Try { cf.createConnection() } match {
        case Success(_) =>
          connected.set(true)
        case Failure(t) =>
          log.debug(s"Not connected to JMS yet ... (${t.getMessage()})")
          connected.set(false)
      }

      connecting.set(false)
    }

    Future(connected.get())
  }
}
