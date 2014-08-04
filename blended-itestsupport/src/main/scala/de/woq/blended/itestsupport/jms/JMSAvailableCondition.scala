package de.woq.blended.itestsupport.jms

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor.Actor.Receive
import akka.pattern.ask
import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import de.woq.blended.itestsupport.CamelTestSupport
import de.woq.blended.itestsupport.condition.{Condition, ConditionChecker}
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.component.mock.MockEndpoint

import scala.concurrent.duration.FiniteDuration

import de.woq.blended.itestsupport.protocol._

class JMSAvailableCondition(
  cf : ConnectionFactory,
  timeout : FiniteDuration
)(implicit system : ActorSystem) extends Condition {

  object JMSConnector {

    val testUri = "jms:topic:blendedTest"
    val mockName = "test"

    def apply(cf: ConnectionFactory) = {

      val connector = new CamelTestSupport {
        override def init() {
          super.init()
          getContext().addComponent("jms", JmsComponent.jmsComponent(cf) )
          wireMock(mockName, testUri)
          getContext().start()
        }
      }

      connector
    }
  }

  implicit val eCtxt = system.dispatcher
  implicit val jmsTimeout = new Timeout(timeout)

  val testSupport  = JMSConnector(cf)
  testSupport.init()

  val jmsAvailable = new AtomicBoolean(false)
  val checker      = system.actorOf(Props(ConditionChecker(this, Props(JMSChecker(this, testSupport)))))

  (checker ? CheckCondition()).mapTo[ConditionSatisfied].map {
    _ => jmsAvailable.set(true)
  }.andThen {
    case _ => {
      system stop checker
      testSupport.getContext.stop()
    }
  }

  override def toString = s"jmsAvailableCondition(${cf})"

  override def satisfied = jmsAvailable.get

  object JMSChecker {
    def apply(condition: Condition, testSupport: CamelTestSupport) = new JMSChecker(condition, testSupport)
  }

  class JMSChecker(condition: Condition, testSupport : CamelTestSupport) extends Actor with ActorLogging {

    import JMSConnector.{testUri, mockName}

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
              val mockEndpoint: MockEndpoint = testSupport.getContext.getEndpoint(s"mock:${mockName}").asInstanceOf[MockEndpoint]
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
