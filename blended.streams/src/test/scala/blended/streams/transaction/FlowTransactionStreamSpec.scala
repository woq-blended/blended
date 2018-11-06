package blended.streams.transaction

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{CollectingActor, Collector}
import blended.streams.transaction.internal.{FlowTransactionManager, FlowTransactionStream}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FlowTransactionStreamSpec extends LoggingFreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  System.setProperty("testName", "stream")

  "The FlowTransactionStream should" - {

    "record an incoming FlowTransactionUpdate correctly" in {

      def singleTest(event : FlowTransactionEvent)(f : List[FlowTransaction] => Unit) : Unit = {

        val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

        withSimpleBlendedContainer(baseDir) { sr =>
          withStartedBundles(sr)(Seq(
            "blended.akka" -> Some(() => new BlendedAkkaActivator())
          )) { sr =>
            implicit val timeout = 1.second
            val idSvc = mandatoryService[ContainerIdentifierService](sr)(None)
            implicit val system = mandatoryService[ActorSystem](sr)(None)

            implicit val eCtxt : ExecutionContext = system.dispatcher
            implicit val materializer : Materializer = ActorMaterializer()
            implicit val log : Logger = Logger[FlowTransactionStreamSpec]

            val tMgr = system.actorOf(FlowTransactionManager.props())

            val transColl = Collector[FlowTransaction]("trans")

            val cfg : FlowHeaderConfig = FlowHeaderConfig.create(
              idSvc.getContainerContext().getContainerConfig().getConfig("blended.flow.header")
            )

            try {
              val good : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { e =>
                val t = FlowTransaction.envelope2Transaction(cfg)(e)
                transColl.actor ! t
                e
              }

              val envelope = FlowTransactionEvent.event2envelope(cfg)(event)
              val source = Source.single[FlowEnvelope](envelope)

              val stream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = new FlowTransactionStream(cfg, tMgr,log, good).build()

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
