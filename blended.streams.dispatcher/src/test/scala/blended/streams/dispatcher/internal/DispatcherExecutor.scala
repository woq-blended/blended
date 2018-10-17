package blended.streams.dispatcher.internal

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import blended.container.context.api.ContainerIdentifierService
import blended.streams.dispatcher.internal.CollectingActor.Completed
import blended.streams.dispatcher.internal.builder.DispatcherBuilder
import blended.streams.dispatcher.internal.worklist.WorklistStarted
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._

case class DispatcherResult(
  out : Seq[FlowEnvelope],
  error: Seq[FlowEnvelope],
  worklist: Seq[WorklistStarted]
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
    val wlProbe = TestProbe()(system)

    val jmsCollector = system.actorOf(Props(new CollectingActor[FlowEnvelope]("out", jmsProbe.ref)))
    val errorCollector = system.actorOf(Props(new CollectingActor[FlowEnvelope]("error", errorProbe.ref)))
    val eventCollector = system.actorOf(Props(new CollectingActor[WorklistStarted]("event", wlProbe.ref)))

    val out = Sink.actorRef[FlowEnvelope](jmsCollector, Completed)
    val error = Sink.actorRef[FlowEnvelope](errorCollector, Completed)
    val worklist = Sink.actorRef[WorklistStarted](eventCollector, Completed)

    val g = DispatcherBuilder.createWithSources(
      idSvc = idSvc,
      dispatcherCfg = cfg,
      source = source,
      envOut = out,
      errorOut = error,
      worklistOut = worklist
    )

    val foo = g.run(materializer)

    DispatcherResult(
      out = jmsProbe.expectMsgType[List[FlowEnvelope]](10.seconds),
      error = errorProbe.expectMsgType[List[FlowEnvelope]](10.seconds),
      worklist = wlProbe.expectMsgType[List[WorklistStarted]](10.seconds)
    )
  }
}
