package blended.streams.transaction

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import blended.akka.internal.BlendedAkkaActivator
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.{CollectingActor, Collector}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RequiresForkedJVM
class FlowTransactionStreamSpec
    extends SimplePojoContainerSpec
    with LoggingFreeSpecLike
    with Matchers
    with PojoSrTestHelper
    with ScalaCheckPropertyChecks {

  System.setProperty("testName", "stream")

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] =
    Seq(
      "blended.akka" -> new BlendedAkkaActivator()
    )

  "The FlowTransactionStream should" - {

    "record an incoming FlowTransactionUpdate correctly" in {

      implicit val system: ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val eCtxt: ExecutionContext = system.dispatcher
      val log: Logger = Logger("spec.flow.stream")

      val tMgr: FlowTransactionManager =
        FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "streamSpec"))

      def singleTest(event: FlowTransactionEvent)(f: List[FlowEnvelope] => Unit): Unit = {

        val transColl =
          Collector[FlowEnvelope](name = "trans", onCollected = Some({ e: FlowEnvelope => e.acknowledge(); true }))

        val cfg: FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)

        try {

          val envelope = FlowTransactionEvent.event2envelope(cfg)(event)
          val source = Source.single[FlowEnvelope](envelope)

          val stream: Flow[FlowEnvelope, FlowEnvelope, NotUsed] = new FlowTransactionStream(
            headerCfg = cfg,
            internalCf = None,
            tMgr = tMgr,
            streamLogger = FlowEnvelopeLogger.create(cfg, log)
          ).build()

          source
            .watchTermination()(Keep.right)
            .viaMat(stream)(Keep.left)
            .toMat(
              Sink.actorRef[FlowEnvelope](transColl.actor, CollectingActor.Success, t => CollectingActor.Failed(t))
            )(Keep.left)
            .run()

          Await.result(transColl.result.map(t => f(t)), 3.seconds)

        } finally {
          system.stop(transColl.actor)
        }
      }

      forAll(FlowTransactionGen.genTrans) { t =>
        val event: FlowTransactionEvent = FlowTransactionStarted(t.tid, t.creationProps)
        singleTest(event) { t => t should have size 1 }
      }
    }
  }
}
