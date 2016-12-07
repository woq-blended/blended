package blended.itestsupport.jms

import javax.jms.ConnectionFactory

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import blended.itestsupport.jms.protocol._

import scala.concurrent.Future
import scala.concurrent.duration._

object JMSAvailableCondition{
  def apply(cf: ConnectionFactory, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) =
    AsyncCondition(Props(JMSChecker(cf)), s"JMSAvailableCondition(${cf})", t)
}

private[jms] object JMSChecker {
  def apply(cf: ConnectionFactory) = new JMSChecker(cf)
}

private[jms]class JMSChecker(cf: ConnectionFactory) extends AsyncChecker {

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  var jmsConnector: Option[ActorRef] = None

  override def preStart() : Unit = {
    jmsConnector = Some(context.actorOf(Props(JMSConnectorActor(cf))))
  }

  override def performCheck(cond: AsyncCondition) : Future[Boolean] = {

    implicit val t = Timeout(10.seconds)
    log.debug("Checking JMS connection...")
    (jmsConnector.get ? Connect("test")).mapTo[Either[JMSCaughtException, Connected]].map { result =>
      result match {
        case Left(e) => {
          log.debug(s"Couldn't connect to JMS [${e.inner.getMessage}]")
          false
        }
        case Right(_) => {
          jmsConnector.get ! Disconnect
          true
        }
      }
    }
  }
}
