package blended.activemq.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.after
import blended.container.context.api.ContainerContext
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.FlowHeaderConfig
import blended.streams.jms.{JmsEnvelopeHeader, JmsProducerSettings, JmsStreamSupport, MessageDestinationResolver}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.Collector
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class RoundtripConnectionVerifier(
  probeMsg : String => FlowEnvelope,
  verify : FlowEnvelope => Boolean,
  requestDest : JmsDestination,
  responseDest : JmsDestination,
  retryInterval : FiniteDuration = 1.second,
  receiveTimeout : FiniteDuration = 250.millis,
  timeToLive : FiniteDuration = 10.seconds
)(implicit system : ActorSystem) extends ConnectionVerifier
  with JmsStreamSupport
  with JmsEnvelopeHeader {

  private val log : Logger = Logger[RoundtripConnectionVerifier]
  private val verified : Promise[Boolean] = Promise[Boolean]()

  override def verifyConnection(ctCtxt : ContainerContext)(cf: IdAwareConnectionFactory)(implicit eCtxt: ExecutionContext): Future[Boolean] = {
    Future {
      probe(ctCtxt)(cf)
    }

    verified.future
  }

  protected def waitForResponse(ctCtxt : ContainerContext)(cf : IdAwareConnectionFactory, id : String) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val headerConfig : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)

    val collector : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerConfig,
      cf = cf,
      dest = responseDest,
      log = FlowEnvelopeLogger.create(headerConfig, log),
      listener = 1,
      selector = Some(s"JMSCorrelationID='$id'"),
      completeOn = Some(_.nonEmpty),
      timeout = Some(receiveTimeout)
    )

    collector.result.onComplete {
      case Success(l) => l match {
        case Nil =>
          log.warn(s"No response received to verify connection [${cf.vendor}:${cf.provider}] with id [$id]")
          scheduleRetry(ctCtxt)(cf)
        case h :: _ =>
          val result : Boolean = verify(h)
          log.info(s"Verification result [$id] for client connection [${cf.vendor}:${cf.provider}] is [$result]")
          verified.complete(Success(result))
      }

      case Failure(t) =>
        log.warn(s"Failed to receive verification response [$id] to verify connection [${cf.vendor}:${cf.provider}] : [${t.getMessage()}]")
        scheduleRetry(ctCtxt)(cf)
    }
  }

  protected def probe(ctCtxt : ContainerContext)(cf: IdAwareConnectionFactory) : Unit = {

    val headerConfig : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)
    val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerConfig, log)
    val id : String = UUID.randomUUID().toString()

    val probeEnv : FlowEnvelope = probeMsg(id)
      .withHeader(corrIdHeader(headerConfig.prefix), id).get
      .withHeader(replyToHeader(headerConfig.prefix), responseDest.asString).get

    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = envLogger,
      headerCfg = headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(requestDest),
      timeToLive = Some(timeToLive),
      destinationResolver = s => new MessageDestinationResolver(s),
      logLevel = _ => LogLevel.Debug
    )

    log.info(s"Running verification probe for connection [${cf.vendor}:${cf.provider}] with id [$id]")

    sendMessages(pSettings, envLogger, timeToLive * 2, probeEnv) match {
      case Success(s) =>
        log.info(s"Request message [$id] sent successfully to [${requestDest.asString}]")
        s.shutdown()

        waitForResponse(ctCtxt)(cf, id)

      case Failure(t) =>
        log.debug(s"Failed to send verification request [$id] to verify connection [${cf.vendor}:${cf.provider}] : [${t.getMessage()}]")
        scheduleRetry(ctCtxt)(cf)
    }
  }

  private def scheduleRetry(ctCtxt : ContainerContext)(cf : IdAwareConnectionFactory) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    after[Unit](retryInterval, system.scheduler){
      log.debug(s"Scheduling retry to verify connection [${cf.vendor}:${cf.provider}] in [$retryInterval]")
      Future { probe(ctCtxt)(cf) }
    }
  }
}
