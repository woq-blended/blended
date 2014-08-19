package de.woq.blended.itestsupport.jms

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import de.woq.blended.itestsupport.camel.CamelTestSupport
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import de.woq.blended.itestsupport.protocol._
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext

import scala.concurrent.duration.FiniteDuration

private object JMSAvailableConditionConstants {
  val testUri = s"jms:topic:blendedTest"
  val mockName = "test"
}

class JMSAvailableCondition(
  cf : ConnectionFactory,
  jmsTimeout : FiniteDuration
)(implicit system : ActorSystem) extends Condition {

  implicit val eCtxt = system.dispatcher

  override def timeout = jmsTimeout

  val jmsAvailable = new AtomicBoolean(false)
  val checker      = system.actorOf(Props(ConditionChecker(this, Props(JMSChecker(this, cf)))))

  (checker ? CheckCondition)(jmsTimeout).mapTo[ConditionSatisfied].map {
    _ => jmsAvailable.set(true)
  }.andThen {
    case _ => {
      system stop checker
    }
  }

  override def toString = s"jmsAvailableCondition(${cf})"

  override def satisfied = jmsAvailable.get

  object JMSChecker {
    def apply(condition: Condition, cf: ConnectionFactory) = new JMSChecker(condition, cf) with CamelTestSupport
  }

  class JMSChecker(condition: Condition, cf: ConnectionFactory) extends Actor with ActorLogging { this : CamelTestSupport =>

    case class CheckJMS(condition: Condition)

    override def supervisorStrategy = OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }

    def receive = idle

    def idle : Receive = LoggingReceive {
      case ConditionTick => {
        val worker = context.actorOf(Props(new Actor with ActorLogging {

          import JMSAvailableConditionConstants._

          var camelContext : Option[CamelContext] = None

          override def preStart() {
            implicit val result = new DefaultCamelContext()
            result.addComponent("jms", JmsComponent.jmsComponent(cf))
            wireMock(mockName, testUri)
            result.start()
            camelContext = Some(result)
          }

          override def postStop() {
            camelContext.foreach(_.stop())
          }

          override def receive : Receive = LoggingReceive {
            case CheckJMS(condition) if camelContext.isDefined => {
              val mockEndpoint: MockEndpoint = camelContext.get.getEndpoint(s"mock:${mockName}").asInstanceOf[MockEndpoint]
              mockEndpoint.reset()
              mockEndpoint.setExpectedMessageCount(1)
              sendTestMessage("Hello Blended!", testUri)(camelContext.get)
              mockEndpoint.assertIsSatisfied(500)
              sender ! ConditionCheckResult(condition, true)
            }
          }
        }))

        context watch worker
        worker ! CheckJMS(condition)
        context become busy(worker, sender)
      }
    }

    def busy(worker: ActorRef, requestor: ActorRef) : Receive = LoggingReceive {
      case ConditionTick =>
      case msg : ConditionCheckResult => {
        context.unwatch(worker)
        context.stop(worker)
        requestor ! msg
        context become idle
      }
      case Terminated(_) => context.become(idle)
    }
  }

}
