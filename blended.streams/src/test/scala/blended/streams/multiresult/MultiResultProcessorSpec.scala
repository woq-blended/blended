package blended.streams.multiresult

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.OverflowStrategy
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.{FlowHeaderConfig, FlowProcessor, StreamFactories}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class MultiResultProcessorSpec extends TestKit(ActorSystem("mulitprocessor"))
  with LoggingFreeSpecLike
  with Matchers {

  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")
  private val log : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, Logger[MultiResultProcessorSpec])
  private val to : FiniteDuration = 10.seconds

  private val createCopies : FlowEnvelope => List[FlowEnvelope] = { env =>
    // scalastyle:off magic.number
    List.tabulate(5)(_ => env)
    // scalastyle:on magic.number
  }

  "The MultiResult processor should" - {

    "produce a single result without exception if all results have been processed successfully" in {

      val successCount : AtomicInteger = new AtomicInteger(0)

      val logOk : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromGraph(FlowProcessor.fromFunction("ok", log){ env => Try {
          successCount.incrementAndGet()
          env
        }})

      val processor : MultiResultProcessor = new MultiResultProcessor(
        replicator = createCopies,
        processSingle = logOk,
        timeout = Some(10.seconds),
        log = log
      )

      // scalastyle:off magic.number
      val src : Source[FlowEnvelope, ActorRef] =
        Source.actorRef[FlowEnvelope](10, OverflowStrategy.fail)
        .viaMat(processor.build())(Keep.left)
      // scalastyle:on magic.number

      val (actor, coll) = StreamFactories.runMatSourceWithTimeLimit[FlowEnvelope, ActorRef](
        name = "replicateOk",
        source = src,
        timeout = Some(to),
        completeOn = Some( l => l.size == 1)
      )

      val env = FlowEnvelope()

      actor ! env

      val result : List[FlowEnvelope] = Await.result(coll.result, 3.seconds)
      result should have size 1
      result.head.exception should be (empty)

      // scalastyle:off magic.number
      successCount.get() should be (5)
      // scalastyle:on magic.number
    }

    "produce a single copy with exception if at least one of the sub flows has produced an exception" in {

      val failed : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromGraph(FlowProcessor.fromFunction("fail", log){ _ => Try {
          throw new Exception("Boom")
        }})

      val processor : MultiResultProcessor = new MultiResultProcessor(
        replicator = createCopies,
        processSingle = failed,
        timeout = None,
        log = log
      )

      // scalastyle:off magic.number
      val src : Source[FlowEnvelope, ActorRef] =
        Source.actorRef[FlowEnvelope](10, OverflowStrategy.fail)
          .viaMat(processor.build())(Keep.left)
      // scalastyle:on magic.number

      val (actor, coll) = StreamFactories.runMatSourceWithTimeLimit[FlowEnvelope, ActorRef](
        name = "replicateFail",
        source = src,
        timeout = Some(to),
        completeOn = Some( l => l.size == 1)
      )

      val env = FlowEnvelope()

      actor ! env

      val result : List[FlowEnvelope] = Await.result(coll.result, 3.seconds)
      result should have size 1
      result.head.exception should be (defined)
    }

    "produce a single copy with a timeout exception if the subflows take too long to respond" in {
      val long : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromGraph(FlowProcessor.fromFunction("fail", log){ env => Try {
          Thread.sleep(10.seconds.toMillis)
          env
        }})

      val processor : MultiResultProcessor = new MultiResultProcessor(
        replicator = createCopies,
        processSingle = long,
        timeout = Some(2.seconds),
        log = log
      )

      // scalastyle:off magic.number
      val src : Source[FlowEnvelope, ActorRef] =
        Source.actorRef[FlowEnvelope](10, OverflowStrategy.fail)
          .viaMat(processor.build())(Keep.left)
      // scalastyle:on magic.number

      val (actor, coll) = StreamFactories.runMatSourceWithTimeLimit[FlowEnvelope, ActorRef](
        name = "replicateFail",
        source = src,
        timeout = Some(to),
        completeOn = Some( l => l.size == 1)
      )

      val env = FlowEnvelope()

      actor ! env

      val result : List[FlowEnvelope] = Await.result(coll.result, 3.seconds)
      result should have size 1
      result.head.exception should be (defined)

      assert(result.head.exception.get.isInstanceOf[MultiResultTimeoutException])
    }
  }
}
