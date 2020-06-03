package blended.streams.transaction

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Zip}
import akka.stream.FlowShape
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.FlowHeaderConfig
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.worklist.WorklistStateCompleted
import blended.util.RichTry._
import blended.util.logging.LogLevel
import javax.jms.Session

import scala.util.Try

class TransactionDestinationResolver(
  override val settings : JmsProducerSettings,
  eventDestName : String
) extends FlowHeaderConfigAware with JmsEnvelopeHeader {

  override def sendParameter(session : Session, env : FlowEnvelope) : Try[JmsSendParameter] = Try {

    val transShard : String = env.header[String](headerConfig.headerTransShard).map { s => s".$s" }.getOrElse("")
    val dest : JmsDestination = JmsDestination.create(eventDestName + transShard).get

    log.debug(s"Transaction destination for [${env.id}] is [$dest]")

    JmsSendParameter(
      message = createJmsMessage(session, env).unwrap,
      destination = dest,
      deliveryMode = settings.deliveryMode,
      priority = settings.priority,
      ttl = None
    )
  }
}

class TransactionWiretap(
  cf : IdAwareConnectionFactory,
  eventDest : JmsDestination,
  headerCfg : FlowHeaderConfig,
  inbound : Boolean,
  trackSource : String,
  log : FlowEnvelopeLogger
)(implicit system: ActorSystem) extends JmsStreamSupport {

  private[transaction] val createTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    def startTransaction(env : FlowEnvelope) : FlowTransactionEvent = {
      FlowTransactionStarted(env.id, env.flowMessage.header)
    }

    def updateTransaction(env : FlowEnvelope) : FlowTransactionEvent = {

      env.exception match {
        case None =>
          val branch = env.header[String](headerCfg.headerBranch).getOrElse("default")
          FlowTransactionUpdate(env.id, env.flowMessage.header, WorklistStateCompleted, branch)

        case Some(e) => FlowTransactionFailed(env.id, env.flowMessage.header, Some(e.getMessage()))
      }
    }

    val g = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
      val event : FlowTransactionEvent = if (inbound) {
        startTransaction(env)
      } else {
        updateTransaction(env)
      }

      log.logEnv(env, LogLevel.Debug, s"Generated bridge transaction event [$event]", false)
      FlowTransactionEvent.event2envelope(headerCfg)(event)
        .withHeader(headerCfg.headerTrackSource, trackSource).unwrap
    }

    Flow.fromGraph(g)
  }

  private[transaction] def transactionSink() : Flow[FlowEnvelope, FlowEnvelope, _] = {

    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val settings = JmsProducerSettings(
        log = log,
        headerCfg = headerCfg,
        connectionFactory = cf,
        destinationResolver = s => new TransactionDestinationResolver(s, JmsDestination.asString(eventDest)),
        deliveryMode = JmsDeliveryMode.Persistent,
        jmsDestination = None,
        logLevel = _ => LogLevel.Debug
      )

      val producer = b.add(jmsProducer(
        name = "event",
        settings = settings,
        autoAck = false
      ))

      val switchOffTracking = b.add(Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
        log.logEnv(env, LogLevel.Trace, s"About to send envelope [$env]")
        env.withHeader(headerCfg.headerTrack, false).unwrap
      })

      switchOffTracking ~> producer
      FlowShape(switchOffTracking.in, producer.out)
    }

    Flow.fromGraph(g)
  }

  def flow(clearException : Boolean = false) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))
      val trans = b.add(createTransaction)
      val sink = b.add(transactionSink())
      val zip = b.add(Zip[FlowEnvelope, FlowEnvelope]())

      val select = b.add(
        Flow.fromFunction[(FlowEnvelope, FlowEnvelope), FlowEnvelope] { pair =>

          if (clearException) {
            pair._2.clearException()
          } else {
            pair._2
          }
        }
      )

      split.out(1) ~> zip.in1
      split.out(0) ~> trans ~> sink ~> zip.in0

      zip.out ~> select

      FlowShape(split.in, select.out)
    }

    Flow.fromGraph(g)
  }
}
