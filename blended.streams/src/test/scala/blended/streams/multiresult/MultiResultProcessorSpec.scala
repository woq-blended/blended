package blended.streams.multiresult

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.testkit.TestKit
import blended.streams.{FlowProcessor, StreamFactories}
import blended.streams.message.FlowEnvelope
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.{LogLevel, Logger}
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class MultiResultProcessorSpec extends TestKit(ActorSystem("mulitprocessor"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log : Logger = Logger[MultiResultProcessorSpec]
  private val to : FiniteDuration = 10.seconds
  private implicit val materializer : Materializer = ActorMaterializer()

  private val createCopies : FlowEnvelope => List[FlowEnvelope] = { env =>
    List.tabulate(5)(_ => env)
  }

  private val failed : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    Flow.fromGraph(FlowProcessor.fromFunction("fail", log){ env => Try {
      throw new Exception("Boom")
    }})

  "The MultiResult processor should" - {

    "produce a single copy without exception if all results have been processed successfully" in {

      val successCount : AtomicInteger = new AtomicInteger(0)

      val logOk : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromGraph(FlowProcessor.fromFunction("ok", log){ env => Try {
          successCount.incrementAndGet()
          env
        }})

      val processor : MultiResultProcessor = new MultiResultProcessor(
        replicator = createCopies,
        processSingle = logOk,
        timeout = None,
        log = log
      )

      val src : Source[FlowEnvelope, ActorRef] =
        Source.actorRef[FlowEnvelope](10, OverflowStrategy.fail)
        .viaMat(processor.build())(Keep.left)

      val (actor, coll) = StreamFactories.runMatSourceWithTimeLimit[FlowEnvelope, ActorRef](
        name = "replicateOk",
        source = src,
        timeout = to,
        completeOn = Some( l => l.size == 1)
      )

      val env = FlowEnvelope()

      actor ! env

      val result : List[FlowEnvelope] = Await.result(coll.result, 3.seconds)
      result should have size 1
      result.head.exception should be (empty)

      successCount.get() should be (5)
    }

    "produce a single copy with exception if at least one of the sub flows has produced an exception" in {
      fail
    }
  }
}
