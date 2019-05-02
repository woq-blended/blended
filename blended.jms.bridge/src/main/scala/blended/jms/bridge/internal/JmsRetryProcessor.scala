package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.stream.{FlowShape, Graph, Materializer}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.streams.transaction.{FlowHeaderConfig, TransactionWiretap}
import blended.streams.{AbstractStreamController, FlowProcessor, StreamController, StreamControllerConfig}
import blended.util.config.Implicits._
import blended.util.logging.{LogLevel, Logger}
import com.typesafe.config.Config
import javax.jms.Session

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object JmsRetryConfig {

  def fromConfig(
    idSvc : ContainerIdentifierService,
    cf : IdAwareConnectionFactory,
    retryDestName : String,
    retryFailedName : String,
    eventDestName : String,
    cfg : Config
  ) : Try[JmsRetryConfig] = Try {

    val retryInterval : FiniteDuration = cfg.getDuration("retryInterval", 1.minutes)
    val maxRetries : Long = cfg.getLong("maxRetries", -1L)
    val retryTimeout : FiniteDuration = cfg.getDuration("retryTimeout", 1.day)

    JmsRetryConfig(
      cf = cf,
      headerCfg = FlowHeaderConfig.create(idSvc),
      retryDestName = retryDestName,
      failedDestName = retryFailedName,
      eventDestName = eventDestName,
      retryInterval = retryInterval,
      maxRetries = maxRetries,
      retryTimeout = retryTimeout
    )
  }
}

case class JmsRetryConfig(
  cf : IdAwareConnectionFactory,
  headerCfg : FlowHeaderConfig,
  retryDestName : String,
  failedDestName : String,
  eventDestName : String,
  retryInterval : FiniteDuration,
  maxRetries : Long = -1,
  retryTimeout : FiniteDuration = 1.day
) {
  override def toString: String = s"${getClass().getSimpleName}[${cf.vendor}:${cf.provider}](retryDestination=$retryDestName," +
    s"failedDestination=$failedDestName,retryInterval=$retryInterval,maxRetries=$maxRetries,retryTimeout=$retryTimeout)"
}

class JmsRetryProcessor(name : String, retryCfg : JmsRetryConfig)(
  implicit system : ActorSystem, materializer : Materializer
) extends JmsStreamSupport {

  private[this] val id : String = retryCfg.headerCfg.prefix + ".retry." + retryCfg.retryDestName
  private[this] val retryLog : Logger = Logger(id)
  private[this] val log : Logger = Logger[JmsRetryProcessor]

  private[this] var actor : Option[ActorRef] = None

  private[this] val router = new JmsRetryRouter("route", retryCfg, retryLog)

  class RetryDestinationResolver(
    override val headerConfig : FlowHeaderConfig,
    override val settings : JmsProducerSettings,
    val validator : FlowProcessor.IntegrationStep
  ) extends FlowHeaderConfigAware with JmsEnvelopeHeader {

    override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

      val dest : JmsDestination = env.exception match {
        // If the envelope does not have an exception, we will send it to the original destination for reprocessing
        // If the header "RetryDestination" is missing, we will send the message to the retry failed queue
        case None =>
          JmsDestination.create(env.headerWithDefault[String](headerConfig.headerRetryDestination, retryCfg.failedDestName)).get

        // If the envelope has an exception, we will try to resend it unless the retry router validation
        // throws an exception (which always means we can't retry the message
        case Some(_) =>
          validator(env) match {
            case Success(_) => JmsDestination.create(retryCfg.retryDestName).get
            case Failure(_) => JmsDestination.create(retryCfg.failedDestName).get
          }
      }

      JmsSendParameter(
        message = createJmsMessage(session, env).get,
        destination = dest,
        deliveryMode = JmsDeliveryMode.Persistent,
        priority = settings.priority,
        ttl = settings.timeToLive
      )
    }
  }

  protected def retrySource : Source[FlowEnvelope, NotUsed] = {
    val settings = JMSConsumerSettings(
      log = retryLog,
      connectionFactory = retryCfg.cf,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = Some(JmsDestination.create(retryCfg.retryDestName).get)
    )

    jmsConsumer(
      name = settings.jmsDestination.get.asString,
      settings = settings,
      headerConfig = retryCfg.headerCfg,
      minMessageDelay = Some(retryCfg.retryInterval)
    )
  }

  protected def resendMessage : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val producerSettings : JmsProducerSettings = JmsProducerSettings(
      log = retryLog,
      connectionFactory = retryCfg.cf,
      destinationResolver = s => new RetryDestinationResolver(retryCfg.headerCfg, s, router.validate),
      deliveryMode = JmsDeliveryMode.Persistent,
      timeToLive = None,
      clearPreviousException = true
    )

    jmsProducer(
      name = name + "routeSend",
      settings = producerSettings,
      autoAck = false,
    )
  }

  protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val wiretap = new TransactionWiretap(
      cf = retryCfg.cf,
      eventDest = JmsDestination.create(retryCfg.eventDestName).get,
      headerCfg = retryCfg.headerCfg,
      inbound = false,
      trackSource = id,
      log = retryLog
    )

    Flow.fromGraph(FlowProcessor.fromFunction(name, retryLog)(router.validate))
      .via(FlowProcessor.log(LogLevel.Debug, retryLog, "Creating transaction failed event"))
      .via(wiretap.flow())
  }

  protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = resendMessage

  protected def sendToRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = resendMessage

  protected def retryGraph : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>

      // determine the retry routing parameters from the message
      val route = b.add(router.flow)

      val routeSend = b.add(sendToOriginal)

      // After determining the retry parameters we send the envelope to either the Retry Destination
      // or the ReryFailed destination
      route.out ~> routeSend.in

      // Partition the normal outcome and the error outcome of forwarding the message
      val routeErrorSplit = b.add(FlowProcessor.partition[FlowEnvelope](_.exception.isEmpty))

      routeSend.out ~> routeErrorSplit.in

      // Merge the splitted branches
      val merge = b.add(Merge[FlowEnvelope](2))

      // If no errors occurred so far, we will simply pass the envelope to acknowledgement
      routeErrorSplit.out0 ~> merge.in(0)

      // In case of an error resending the message to the original JMS Destination we will
      // resend it to the end of the retry destination right away
      val retrySend = b.add(sendToRetry)
      routeErrorSplit.out1 ~> retrySend ~> merge.in(1)

      // After sending processing the message we check whether we need to send a
      // transaction failed event. We only need to send a transaction failed event in
      // case the envelope is marked with an exception after trying to forward the
      // message

      val transSplit = b.add(FlowProcessor.partition[FlowEnvelope]{ env => env.exception.isEmpty && router.validate(env).isFailure })
      val transMerge = b.add(Merge[FlowEnvelope](2))

      merge.out ~> transSplit.in

      transSplit.out0 ~> sendTransaction ~> transMerge.in(0)
      transSplit.out1 ~> transMerge.in(1)

      // Acknowledge / Deny the result of the overall retry flow
      val ack = b.add(
        Flow.fromGraph(FlowProcessor.log(LogLevel.Debug, retryLog, "Before Acknowledge"))
          .via(new AckProcessor(name + ".ack").flow)
      )
      transMerge.out ~> ack.in

      // Finally we hook up the dangling endpoints of the flow
      new FlowShape[FlowEnvelope, FlowEnvelope](route.in, ack.out)
    }
  }

  def start() : Unit = {

    actor.synchronized {
      if (actor.isEmpty) {
        log.info(s"Starting Jms Retry processor [$name] with [$retryCfg]")

        // TODO: Load from config
        val streamCfg : StreamControllerConfig = StreamControllerConfig(
          name = name,
          //source = retrySource.via(retryGraph),
          minDelay = 10.seconds,
          maxDelay = 3.minutes,
          exponential = true,
          onFailureOnly = true,
          random = 0.2
        )

        actor = Some(system.actorOf(StreamController.props[FlowEnvelope, NotUsed](retrySource.via(retryGraph), streamCfg)))
      }
    }
  }

  def stop(): Unit = {
    actor.synchronized{
      actor.foreach(system.stop)
      actor = None
    }
  }
}
