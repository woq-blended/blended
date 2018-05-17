package blended.testsupport.camel

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging}
import akka.camel.{CamelExtension, CamelMessage}
import blended.testsupport.camel.protocol._
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.{Exchange, Processor}

import scala.collection.convert.Wrappers.JMapWrapper
import scala.concurrent.duration._

object CamelMockActor {

  val counter : AtomicInteger = new AtomicInteger(0)
  def apply(uri: String) = new CamelMockActor(uri, counter.incrementAndGet())
}

class CamelMockActor(uri: String, id : Int) extends Actor with ActorLogging {

  private[this] val camelContext = CamelExtension(context.system).context
  private[this] implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def preStart(): Unit = {
    log.debug(s"Starting Camel Mock Actor for [$id, $uri]")

    val routeId = UUID.randomUUID().toString()

    camelContext.addRoutes( new RouteBuilder() {

      override def configure(): Unit = {

        val mockActor = self

        context.system.log.info(s"Starting mock route on [$uri] with id [$id] : [$routeId]")

        from(uri)
          .id(routeId)
          .process(new Processor {
          override def process(exchange: Exchange): Unit = {
            val header = JMapWrapper(exchange.getIn().getHeaders()).filter { case (k,v) => Option(v).isDefined }.toMap
            val msg = CamelMessage(exchange.getIn().getBody(), header)
            mockActor ! msg
          }
        })

        self ! Tick
        context.become(starting(routeId))

        log.debug(s"Configured mock route for [$toString]")
      }
    })
  }

  override def receive = Actor.emptyBehavior

  def starting(routeId : String) : Receive = {
    case Tick =>
      if (camelContext.getRouteStatus(routeId).isStarted()) {
        context.system.eventStream.publish(MockActorReady(uri))
        context.become(receiving(routeId)(List.empty) orElse (handleRquests(List.empty)))
      } else {
        context.system.scheduler.scheduleOnce(10.millis, self, Tick)
      }
  }

  def handleRquests(messages: List[CamelMessage]) : Receive = {
    case GetReceivedMessages => sender ! ReceivedMessages(messages)

    case ca : CheckAssertions =>
      val requestor = sender()
      val mockMessages = messages.reverse
      log.info(s"Checking assertions for [$id, $uri] on [${mockMessages.size}] messages.")
      val results = CheckResults(ca.assertions.toList.map { a => a.f(mockMessages) })
      errors(results) match {
        case e if e.isEmpty =>
        case l => log.error(prettyPrint(l))
      }
      log.debug(s"Sending assertion results to caller : [$results]")
      requestor ! results
  }

  def receiving(routeId : String)(messages: List[CamelMessage]) : Receive = {
    case msg : CamelMessage =>
      val newList = msg :: messages

      log.info(s"CamelMockActor [$id, $uri] received message with Headers [${msg.headers.mkString(",")}]")
      log.info(s"CamelMockActor [$id, $uri] has now [${newList.size}] messages.")
      context.become(receiving(routeId)(newList) orElse (handleRquests(newList)))
      context.system.eventStream.publish(MockMessageReceived(uri, msg))

    case StopReceive =>
      log.info(s"Stopping route for [$id, $uri] [$routeId]")
      camelContext.stopRoute(routeId)
      camelContext.removeRoute(routeId)

      context.system.eventStream.publish(ReceiveStopped(uri))
      context.become(handleRquests(messages))
  }

  private[this] def prettyPrint(errors : List[Throwable]) : String =
    errors match {
      case e if e.isEmpty => s"All assertions were satisfied for mock actor [$uri]"
      case l => l.map(t => s"  ${t.getMessage}").mkString(s"\n${"-" * 80}\nGot Assertion errors for mock actor [$uri]:\n", "\n", s"\n${"-" * 80}")
    }

  private[this] def errors(r : CheckResults) : List[Throwable] = r.results.filter(_.isFailure).map(_.failed.get)

  override def toString: String = s"CamelMockActor[$id, $uri]"
}