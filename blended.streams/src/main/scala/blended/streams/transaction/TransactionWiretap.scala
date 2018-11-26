package blended.streams.transaction

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, Materializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Zip}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.FlowProcessor
import blended.streams.jms.{JmsDeliveryMode, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.FlowEnvelope
import blended.streams.worklist.WorklistState
import blended.util.logging.Logger

import scala.util.Try

class TransactionWiretap(
  cf : IdAwareConnectionFactory,
  eventDest : JmsDestination,
  headerCfg : FlowHeaderConfig,
  inbound : Boolean,
  trackSource : String,
  log : Logger
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  private[transaction] val createTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    def startTransaction(env: FlowEnvelope) : FlowTransactionEvent = {
      FlowTransactionStarted(env.id, env.flowMessage.header)
    }

    def updateTransaction(env: FlowEnvelope) : FlowTransactionEvent = {

      env.exception match {
        case None =>
          val branch = env.header[String](headerCfg.headerBranch).getOrElse("default")
          FlowTransactionUpdate(env.id, env.flowMessage.header, WorklistState.Completed, branch)

        case Some(e) => FlowTransactionFailed(env.id, env.flowMessage.header,  Some(e.getMessage()))
      }
    }

    val g = FlowProcessor.fromFunction("createTransaction", log){ env =>
      Try {
        val event : FlowTransactionEvent = if (inbound) {
          startTransaction(env)
        } else {
          updateTransaction(env)
        }

        log.debug(s"Generated bridge transaction event [$event]")
        FlowTransactionEvent.event2envelope(headerCfg)(event)
          .withHeader(headerCfg.headerTrackSource, trackSource).get

      }
    }

    Flow.fromGraph(g)
  }

  private[transaction] def transactionSink() : Flow[FlowEnvelope, FlowEnvelope, _] = {

    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val settings = JmsProducerSettings(
        connectionFactory = cf,
        deliveryMode = JmsDeliveryMode.Persistent,
        jmsDestination = Some(eventDest)
      )

      val producer = b.add(jmsProducer(
        name = "event",
        settings = settings,
        autoAck = false,
        log = log
      ))

      val switchOffTracking = b.add(Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
        log.debug(s"About to send envelope [$env]")
        env.withHeader(headerCfg.headerTrack, false).get
      })

      switchOffTracking ~> producer
      FlowShape(switchOffTracking.in, producer.out)
    }

    Flow.fromGraph(g)
  }

  def flow() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))
      val trans = b.add(createTransaction)
      val sink = b.add(transactionSink())
      val zip = b.add(Zip[FlowEnvelope, FlowEnvelope]())
      val select = b.add(Flow.fromFunction[(FlowEnvelope, FlowEnvelope), FlowEnvelope]{ _._2 })

      split.out(1) ~> zip.in1
      split.out(0) ~> trans ~> sink ~> zip.in0

      zip.out ~> select

      FlowShape(split.in, select.out)
    }

    Flow.fromGraph(g)
  }
}
