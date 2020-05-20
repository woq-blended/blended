package blended.jms.bridge.internal

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.{FlowShape, Graph, KillSwitch, Materializer}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.FlowEnvelope
import blended.streams.{BlendedStreamsConfig, FlowProcessor}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

/**
 * Verify that a message is routed correctly after it has been redirected through the
 * retry queue at least once.
 */
@RequiresForkedJVM
class RouteAfterRetrySpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withRetries").getAbsolutePath()

  private def sendOutbound(cf : IdAwareConnectionFactory, msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(headerCfg.headerBridgeVendor, "activemq").get
        .withHeader(headerCfg.headerBridgeProvider, "external").get
        .withHeader("SIBBridgeDestination", "sampleOut").get
        //.withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out", cf)(msgs:_*)
  }

  // We override the send flow with a flow simply triggering an exception, so that the
  // exceptional path will be triggered
  override protected def bridgeActivator: BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system: ActorSystem)(materializer: Materializer)(
      cfg: BridgeStreamConfig, streamsCfg : BlendedStreamsConfig
    ): BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg, streamsCfg)(system, materializer) {

        override protected def jmsSend: Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

          val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
            import GraphDSL.Implicits._

            val partition = b.add(FlowProcessor.partition[FlowEnvelope]{ env =>
              env.header[Long](headerCfg.headerRetryCount).getOrElse(0L) < 2
            })

            val merge = b.add(Merge[FlowEnvelope](2))

            val fail = b.add(
              Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env.withException(new Exception("Boom")) }
            )

            val send = b.add(super.jmsSend)

            partition.out0 ~> fail ~> merge.in(0)
            partition.out1 ~> send ~> merge.in(1)

            FlowShape(partition.in, merge.out)
          }

          Flow.fromGraph(g)
        }
      }
  }

  "The outbound bridge should " - {

    "correctly route outbound messages after one or more retries" in {
      val timeout : FiniteDuration = 1.second
      val msgCount = 1

      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val switch = sendOutbound(internal, msgCount, true)

      println("Waiting for the retry loop to complete ...")
      0.to(15).foreach{ i =>
        Thread.sleep(1000)
        print(s"-")
      }
      println()

      val retried : List[FlowEnvelope] = consumeMessages(
        cf = internal,
        destName = "retries",
        timeout = timeout
      )(actorSys).get
      retried should be (empty)

      consumeEvents(internal, timeout)(actorSys).get should not be empty

      val messages : List[FlowEnvelope] =
        consumeMessages(
          cf = external,
          destName = "sampleOut",
          expected = msgCount,
          timeout = timeout
        )(actorSys).get

      messages should have size(msgCount)

      switch.shutdown()
    }
  }
}
