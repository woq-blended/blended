package blended.streams.dispatcher.internal

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import blended.container.context.api.ContainerIdentifierService
import blended.streams.dispatcher.DispatcherBuilder
import blended.streams.dispatcher.internal.CollectingActor.Completed
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

case class DispatcherResult(
  out : Seq[FlowEnvelope],
  error: Seq[FlowEnvelope],
  event: Seq[FlowEnvelope]
)

object DispatcherExecutor {

  private val log = Logger[DispatcherExecutor.type]

  def execute(
    system: ActorSystem,
    idSvc : ContainerIdentifierService,
    cfg: ResourceTypeRouterConfig,
    testMessages : FlowEnvelope*
  ) : DispatcherResult = {

    val materializer = ActorMaterializer()(system)

    val source: Source[FlowEnvelope, NotUsed] = Source(testMessages.toList)

    val jmsProbe = TestProbe()(system)
    val errorProbe = TestProbe()(system)
    val eventProbe = TestProbe()(system)

    val jmsCollector = system.actorOf(Props(new CollectingActor("out", jmsProbe.ref)))
    val errorCollector = system.actorOf(Props(new CollectingActor("error", errorProbe.ref)))
    val eventCollector = system.actorOf(Props(new CollectingActor("event", eventProbe.ref)))

    val out = Sink.actorRef[FlowEnvelope](jmsCollector, Completed)
    val error = Sink.actorRef[FlowEnvelope](errorCollector, Completed)
    val event = Sink.actorRef[FlowEnvelope](eventCollector, Completed)

    val g = DispatcherBuilder(
      idSvc = idSvc,
      cfg = cfg,
      source = source,
      jmsOut = out,
      errorOut = error,
      eventOut = event
    ).build()

    val foo = g.run(materializer)

    DispatcherResult(
      out = jmsProbe.expectMsgType[List[FlowEnvelope]],
      error = errorProbe.expectMsgType[List[FlowEnvelope]],
      event = eventProbe.expectMsgType[List[FlowEnvelope]]
    )
  }
}
