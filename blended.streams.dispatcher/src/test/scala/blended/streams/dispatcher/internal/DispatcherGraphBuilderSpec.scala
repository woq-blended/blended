package blended.streams.dispatcher.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import blended.streams.dispatcher.DispatcherBuilder
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger

import scala.collection.mutable

class DispatcherGraphBuilderSpec extends TestKit(ActorSystem("dispatcher"))
  with LoggingFreeSpecLike {

  private[this] val log = Logger[DispatcherGraphBuilderSpec]

  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val eCtxt = system.dispatcher

  case object Completed

  class CollectingActor(actor: ActorRef) extends Actor {

    val envelopes : mutable.Buffer[FlowEnvelope] = mutable.Buffer.empty

    override def receive: Receive = {
      case env: FlowEnvelope =>
        envelopes += env
      case Completed =>
        actor ! envelopes.toList
    }
  }

  "The Dispatcher RouteBuilder should" - {

    "split between normal and error messages" in {

      val good = FlowEnvelope(FlowMessage("Normal", FlowMessage.noProps))
      val bad = FlowEnvelope(FlowMessage("Error", FlowMessage.noProps)).withException(new Exception("Boom"))

      val source : Source[FlowEnvelope, NotUsed] = Source(List(good, bad))

      val jmsProbe = TestProbe()
      val errorProbe = TestProbe()

      val jmsCollector = system.actorOf(Props(new CollectingActor(jmsProbe.ref)))
      val errorCollector = system.actorOf(Props(new CollectingActor(errorProbe.ref)))

      val out = Sink.actorRef[FlowEnvelope](jmsCollector, Completed)
      val error = Sink.actorRef[FlowEnvelope](errorCollector, Completed)

      val g = DispatcherBuilder(source, out, error).build()

      val foo = g.run(materializer)

      jmsProbe.expectMsg(List(good))
      errorProbe.expectMsg(List(bad))

    }
  }
}
