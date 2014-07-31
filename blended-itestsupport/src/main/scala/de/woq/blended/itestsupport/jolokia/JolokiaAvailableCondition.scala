package de.woq.blended.itestsupport.jolokia

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor._
import akka.pattern._
import akka.event.LoggingReceive
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import de.woq.blended.itestsupport.protocol._
import de.woq.blended.jolokia.model.JolokiaVersion
import de.woq.blended.jolokia.protocol._
import de.woq.blended.jolokia.{JolokiaAddress, JolokiaClient}

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

class JolokiaAvailableCondition(
  url: String,
  timeout: FiniteDuration,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system: ActorSystem) extends Condition {

  object JolokiaConnector {
    def apply(url: String, userName: Option[String], userPwd: Option[String]) =
      new JolokiaClient with JolokiaAddress {
        override val jolokiaUrl = url
        override val user       = userName
        override val password   = userPwd
      }
  }

  implicit val eCtxt = system.dispatcher
  implicit val jolokiaTimeout = new Timeout(timeout)

  val jolokiaAvailable = new AtomicBoolean(false)
  val connector        = system.actorOf(Props(JolokiaConnector(url, userName, userPwd)))
  val checker          = system.actorOf(Props(ConditionChecker(this, Props(JolokiaChecker(this, connector)))))

  (checker ? CheckCondition()).mapTo[ConditionCheckResult].onComplete {
    case Success(ConditionCheckResult(_, available)) => jolokiaAvailable.set(available)
    case _ =>
  }

  override def toString = s"JolokiaAvailableCondition(${url})"

  override def satisfied = {
    jolokiaAvailable.get
  }

  object JolokiaChecker {
    def apply(condition: Condition, connector: ActorRef) = new JolokiaChecker(condition, connector)
  }

  class JolokiaChecker(condition: Condition, connector: ActorRef) extends Actor with ActorLogging {

    var requestor : Option[ActorRef] = None

    def receive = LoggingReceive {
      case ConditionTick => {
        requestor = Some(sender)
        (connector ? GetJolokiaVersion).mapTo[JolokiaVersion].onSuccess {
          case version => requestor.foreach(_ ! ConditionCheckResult(condition, true))
        }
      }
    }
  }
}

