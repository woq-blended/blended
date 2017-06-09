package blended.itestsupport.jms

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor._
import akka.util.Timeout
import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import blended.jms.utils.JMSSupport

import scala.concurrent.Future
import scala.concurrent.duration._

object JMSAvailableCondition{
  def apply(cf: ConnectionFactory, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) =
    AsyncCondition(Props(JMSChecker(cf)), s"JMSAvailableCondition(${cf})", t)
}

private[jms] object JMSChecker {
  def apply(cf: ConnectionFactory) = new JMSChecker(cf)
}

private[jms]class JMSChecker(cf: ConnectionFactory) extends AsyncChecker with JMSSupport {

  var connected : AtomicBoolean = new AtomicBoolean(false)
  var connecting : AtomicBoolean = new AtomicBoolean(false)

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def performCheck(cond: AsyncCondition) : Future[Boolean] = {

    implicit val t = Timeout(5.seconds)

    log.debug(s"Checking JMS connection...[$cf]")


    if ( (!connected.get()) && (!connecting.get()) ) {
      connecting.set(true)

      withConnection { conn =>
        connected.set(true)
      } (cf)

      connecting.set(false)
    }

    Future(connected.get())
  }
}
