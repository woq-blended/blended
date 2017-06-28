package blended.testsupport.camel

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.camel.{CamelExtension, CamelMessage}
import blended.testsupport.camel.protocol._
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.{Exchange, Processor}

import scala.collection.convert.Wrappers.JMapWrapper

object CamelMockActor {
  def apply(uri: String) = new CamelMockActor(uri)
}

class CamelMockActor(uri: String) extends Actor with ActorLogging {

  private[this] val camelContext = CamelExtension(context.system).context
  private[this] var routeId : Option[String] = None

  override def preStart(): Unit = {
    log.debug("Starting Camel Mock Actor for [{}]", uri)

    camelContext.addRoutes( new RouteBuilder() {

      override def configure(): Unit = {

        val mockActor = self

        routeId = Some(UUID.randomUUID().toString())

        routeId.foreach { rid =>
          context.system.log.debug("Starting mock route {}", rid)
          from(uri)
            .id(rid)
            .process(new Processor {
            override def process(exchange: Exchange): Unit = {
              val header = JMapWrapper(exchange.getIn().getHeaders()).filter { case (k,v) => Option(v).isDefined }.toMap
              val msg = CamelMessage(exchange.getIn().getBody(), header)
              mockActor ! msg
            }
          })
        }
      }
    })
    super.preStart()
  }

  override def receive: Actor.Receive = receiving(List.empty) orElse (handleRquests(List.empty))

  def handleRquests(messages: List[CamelMessage]) : Receive = {
    case GetReceivedMessages => sender ! ReceivedMessages(messages)

    case ca : CheckAssertions =>
      val requestor = sender()
      val mockMessages = messages.reverse
      log.info(s"Checking assertions for [$uri] on [${mockMessages.size}] messages.")
      val results = CheckResults(ca.assertions.toList.map { a => a(mockMessages) })
      errors(results) match {
        case e if e.isEmpty =>
        case l => log.error(prettyPrint(l))
      }
      log.debug(s"Sending assertion results to caller : [$results]")
      requestor ! results
  }

  def receiving(messages: List[CamelMessage]) : Receive = {
    case msg : CamelMessage =>
      val newList = msg :: messages

      log.info(s"CamelMockActor received message with Headers [${msg.headers.mkString}]")
      log.info(s"CamelMockActor [$uri] has now [${newList.size}] messages.")
      context.become(receiving(newList) orElse (handleRquests(newList)))
      context.system.eventStream.publish(MockMessageReceived(uri, msg))

    case StopReceive =>
      routeId.foreach { rid =>
        log.debug("Stopping route [{}]", rid)
        camelContext.stopRoute(rid)
        camelContext.removeRoute(rid)
      }
      sender ! ReceiveStopped(uri)
      context.become(handleRquests(messages))
  }

  private[this] def prettyPrint(errors : List[String]) : String =
    errors match {
      case e if e.isEmpty => s"All assertions were satisfied for mock actor [$uri]"
      case l => l.map(msg => s"  $msg").mkString(s"\n----------\nGot Assertion errors for mock actor [$uri]:\n", "\n", "\n----------")
    }
  
    
  private[this] def errors(r : CheckResults) : List[String] = r.results.collect {
    case Left(t) => t.getMessage 
  }

  override def toString: String = s"CamelMockActor[$uri]"
}