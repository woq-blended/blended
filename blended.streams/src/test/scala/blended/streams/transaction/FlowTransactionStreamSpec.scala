package blended.streams.transaction

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestKit
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{CollectingActor, Collector}
import blended.streams.transaction.internal.{FlowTransactionManager, FlowTransactionStream}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}

class FlowTransactionStreamSpec extends TestKit(ActorSystem("stream"))
  with LoggingFreeSpecLike
  with Matchers
  with BeforeAndAfterAll {

  private implicit val actorSystem : ActorSystem = system
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val log : Logger = Logger[FlowTransactionStreamSpec]

  private val tMgr = system.actorOf(FlowTransactionManager.props())

  private val cfg : FlowHeaderConfig = FlowHeaderConfig.create(ConfigFactory.parseMap(
    Map(
      "prefix" -> "App",
      "transactionId" -> "AppFlowTransId",
      "transactionState" -> "AppFlowTransState",
      "branchId" -> "AppFlowBranch"
    ).asJava
  ))

  override protected def afterAll(): Unit = Await.result(system.terminate(), 3.seconds)


  "The FlowTransactionStream should" - {

    "record an incoming FlowTransactionUpdate correctly" in {

      def singleTest(event : FlowTransactionEvent)(f : List[FlowTransaction] => Unit) : Unit = {

        val transColl = Collector[FlowTransaction]("trans")

        try {
          val good : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { e =>
            val t = FlowTransaction.envelope2Transaction(cfg)(e)
            transColl.actor ! t
            e
          }

          val envelope = FlowTransactionEvent.event2envelope(cfg)(event)
          val source = Source.single[FlowEnvelope](envelope)

          val stream : Sink[FlowEnvelope, NotUsed] = new FlowTransactionStream(cfg, tMgr, good).build()

          source
            .watchTermination()(Keep.right)
            .toMat(stream)(Keep.left)
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
