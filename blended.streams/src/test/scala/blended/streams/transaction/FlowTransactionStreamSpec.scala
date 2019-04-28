package blended.streams.transaction

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.persistence.PersistenceService
import blended.persistence.h2.internal.H2Activator
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{CollectingActor, Collector}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@RequiresForkedJVM
class FlowTransactionStreamSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  System.setProperty("testName", "stream")

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.persistence.h2" -> new H2Activator()
  )

  "The FlowTransactionStream should" - {

    "record an incoming FlowTransactionUpdate correctly" in {

      def singleTest(event : FlowTransactionEvent)(f : List[FlowTransaction] => Unit) : Unit = {

        implicit val timeout = 1.second
        val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)
        implicit val system = mandatoryService[ActorSystem](registry)(None)
        val pSvc : PersistenceService = mandatoryService[PersistenceService](registry)(None)

        implicit val eCtxt : ExecutionContext = system.dispatcher
        implicit val materializer : Materializer = ActorMaterializer()
        implicit val log : Logger = Logger("spec.flow.stream")

        val tMgr = system.actorOf(FlowTransactionManager.props(pSvc))

        val transColl = Collector[FlowTransaction]("trans")(_ => {})

        val cfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

        try {
          val good : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { e =>
            val t = FlowTransaction.envelope2Transaction(cfg)(e)
            transColl.actor ! t
            e
          }

          val envelope = FlowTransactionEvent.event2envelope(cfg)(event)
          val source = Source.single[FlowEnvelope](envelope)

          val stream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = new FlowTransactionStream(
            headerCfg = cfg,
            internalCf = None,
            tMgr = tMgr,
            streamLogger = log,
            performSend = env => env.header[Boolean](cfg.prefix + "CbeEnabled").getOrElse(true),
            sendFlow = good
          ).build()

          source
            .watchTermination()(Keep.right)
            .viaMat(stream)(Keep.left)
            .toMat(Sink.ignore)(Keep.left)
            .run()

          akka.pattern.after(1.second, system.scheduler)(Future {transColl.actor ! CollectingActor.Completed })
          Await.result(transColl.result.map(t => f(t)), 3.seconds)
        } finally {
          system.stop(transColl.actor)
        }
      }

      singleTest(FlowTransaction.startEvent()){ t =>
        t should have size 1
        t.head.worklist should be (empty)
        t.head.state should be (FlowTransactionState.Started)
      }
    }
  }
}
