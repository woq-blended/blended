package de.woq.blended.itestsupport.jolokia

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import de.woq.blended.itestsupport.protocol._
import de.woq.blended.jolokia.{JolokiaAddress, JolokiaClient}

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure

trait JolokiaAssertion {
  def jolokiaRequest : Any
  def assertJolokia  : Any => Boolean
}

class JolokiaCondition (
  url: String,
  timeout: FiniteDuration,
  userName: Option[String] = None,
  userPwd: Option[String] = None
)(implicit system: ActorSystem) extends Condition { this : JolokiaAssertion =>

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

  val jolokiaAsserted  = new AtomicBoolean(false)
  val connector        = system.actorOf(Props(JolokiaConnector(url, userName, userPwd)))
  val checker          = system.actorOf(Props(ConditionChecker(this, Props(JolokiaChecker(this, connector)))))

  (checker ? CheckCondition()).mapTo[ConditionSatisfied].map {
    _ => jolokiaAsserted.set(true)
  }.andThen {
    case _ => Seq(connector, checker).foreach { system.stop(_) }
  }

  override def toString = s"JolokiaAvailableCondition(${url})"

  override def satisfied = jolokiaAsserted.get

  object JolokiaChecker {
    def apply(condition: Condition, connector: ActorRef) = new JolokiaChecker(condition, connector)
  }

  class JolokiaChecker(condition: Condition, connector: ActorRef) extends Actor with ActorLogging {

    def receive = idle

    def idle : Receive = LoggingReceive {
      case ConditionTick => {
        context become(busy(sender))
        connector ! jolokiaRequest
      }
    }

    def busy(requestor: ActorRef) : Receive = LoggingReceive {
      case ConditionTick =>
      case msg  => {
        if (assertJolokia(msg)) {
          requestor ! ConditionCheckResult(condition, true)
          context.stop(self)
        } else {
          context.become(idle)
        }
      }
    }
  }
}



