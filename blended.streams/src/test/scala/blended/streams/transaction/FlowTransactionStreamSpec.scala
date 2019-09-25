package blended.streams.transaction

import java.io.File

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jmx.statistics.StatisticData
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{CollectingActor, Collector}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.{FlowHeaderConfig, StreamFactories}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

@RequiresForkedJVM
class FlowTransactionStreamSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with PropertyChecks {

  System.setProperty("testName", "stream")

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private implicit val timeout : FiniteDuration = 1.second
  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()
  private val log : Logger = Logger("spec.flow.stream")

  private val tMgr : FlowTransactionManager =
    FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "streamSpec"))

  "The FlowTransactionStream should" - {

    "record an incoming FlowTransactionUpdate correctly" in {

      def singleTest(event : FlowTransactionEvent)(f : List[FlowEnvelope] => Unit) : Unit = {

        val transColl = Collector[FlowEnvelope](name = "trans", onCollected = Some({ e : FlowEnvelope => e.acknowledge() }))

        val statSrc : Source[StatisticData, ActorRef] = Source.actorRef(10, OverflowStrategy.fail)
        val (statActor, dataColl) = StreamFactories.runMatSourceWithTimeLimit[StatisticData, ActorRef](
          name = "statColl",
          source = statSrc,
          timeout = 3.seconds,
          completeOn = Some(s => s.size == 1)
        )

        val cfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

        try {

          system.eventStream.subscribe(statActor, classOf[StatisticData])

          val envelope = FlowTransactionEvent.event2envelope(cfg)(event)
          val source = Source.single[FlowEnvelope](envelope)

          val stream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = new FlowTransactionStream(
            headerCfg = cfg,
            internalCf = None,
            tMgr = tMgr,
            streamLogger = log,
          ).build()

          source
            .watchTermination()(Keep.right)
            .viaMat(stream)(Keep.left)
            .toMat(Sink.actorRef[FlowEnvelope](transColl.actor, CollectingActor.Completed))(Keep.left)
            .run()

          Await.result(transColl.result.map(t => f(t)), 3.seconds)

          val data = Await.result(dataColl.result, 1.seconds)
          data should have size 1


        } finally {
          system.stop(transColl.actor)
        }
      }

      forAll(FlowTransactionGen.genTrans) {t =>
        val event : FlowTransactionEvent = FlowTransactionStarted(t.tid, t.creationProps)
        singleTest(event){ t => t should have size 1 }
      }
    }
  }
}
