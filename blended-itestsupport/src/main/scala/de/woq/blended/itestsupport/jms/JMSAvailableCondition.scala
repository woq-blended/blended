package de.woq.blended.itestsupport.jms

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import de.woq.blended.itestsupport.camel.{CamelContextProvider, CamelTestSupport}
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import de.woq.blended.itestsupport.protocol._
import org.apache.camel.Component
import org.apache.camel.component.jms
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

private object JMSAvailableConditionConstants {
  val testUri = s"jms:topic:blendedTest"
  val mockName = "test"
}

class JMSAvailableCondition(
  cf : ConnectionFactory,
  jmsTimeout : FiniteDuration
)(implicit system : ActorSystem) extends Condition {

  import JMSAvailableConditionConstants._

  class JMSConnector(cf: ConnectionFactory) extends CamelTestSupport with CamelContextProvider {

    override val camelComponents = {
      val builder = new mutable.MapBuilder[String, Component, Map[String, Component]](Map.empty)
      builder += ("jms" -> JmsComponent.jmsComponent(cf))
      builder.result().toMap
    }
  }

  override def timeout = jmsTimeout

  implicit val eCtxt = system.dispatcher

  val testSupport = new JMSConnector(cf)
  testSupport.wireMock(mockName, testUri)

  val jmsAvailable = new AtomicBoolean(false)
  val checker      = system.actorOf(Props(ConditionChecker(this, Props(JMSChecker(this, testSupport)))))

  (checker ? CheckCondition)(jmsTimeout).mapTo[ConditionSatisfied].map {
    _ => jmsAvailable.set(true)
  }.andThen {
    case _ => {
      system stop checker
      testSupport.testContext.stop()
    }
  }

  override def toString = s"jmsAvailableCondition(${cf})"

  override def satisfied = jmsAvailable.get

  object JMSChecker {
    def apply(condition: Condition, testSupport: CamelTestSupport) = new JMSChecker(condition, testSupport)
  }

  class JMSChecker(condition: Condition, testSupport : CamelTestSupport) extends Actor with ActorLogging {

    import JMSAvailableConditionConstants._

    case class CheckJMS(condition: Condition, testSupport: CamelTestSupport)

    override def supervisorStrategy = OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }

    def receive = idle

    def idle : Receive = LoggingReceive {
      case ConditionTick => {
        val worker = context.actorOf(Props(new Actor with ActorLogging {

          override def receive : Receive = LoggingReceive {

            case CheckJMS(condition, testSupport) => {
              val mockEndpoint: MockEndpoint = testSupport.testContext.getEndpoint(s"mock:${mockName}").asInstanceOf[MockEndpoint]
              mockEndpoint.reset()
              mockEndpoint.setExpectedMessageCount(1)
              testSupport.sendTestMessage("Hello Blended!", testUri)
              mockEndpoint.assertIsSatisfied(500)
              sender ! ConditionCheckResult(condition, true)
            }
          }

        }))
        context watch worker
        worker ! CheckJMS(condition, testSupport)
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
