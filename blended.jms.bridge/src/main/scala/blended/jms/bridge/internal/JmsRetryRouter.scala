package blended.jms.bridge.internal

import java.text.SimpleDateFormat
import java.util.Date

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.streams.FlowProcessor
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.FlowHeaderConfig
import blended.util.logging.LogLevel

import scala.concurrent.duration._
import scala.util.Try

class RetryCountExceededException(n : Long)
  extends Exception(s"Maximum Retry [$n] count exceeded")

class RetryTimeoutException(t : Long)
  extends Exception(s"Retry timeout [${new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(t))}] exceeded")

class MissingRetryDestinationException(d : String)
  extends Exception(s"The retry destination header [$d] is missing in the message.")

class MissingHeaderException(h : String)
  extends Exception(s"The envelope is missing the header ($h)")

class JmsRetryRouter(
  name : String,
  retryCfg : JmsRetryConfig,
  log : FlowEnvelopeLogger
) {

  private[this] val headerCfg : FlowHeaderConfig = retryCfg.headerCfg

  // make sure all headers are set without encountering an exception
  val header : FlowProcessor.IntegrationStep = env => Try {

    val maxRetries : Long = env.header[Long](headerCfg.headerMaxRetries).getOrElse(retryCfg.maxRetries)
    val retryCount : Long = env.header[Long](headerCfg.headerRetryCount).getOrElse(0L) + 1
    val retryTimeout : Long = env.header[Long](headerCfg.headerRetryTimeout).getOrElse(retryCfg.retryTimeout.toMillis)
    val firstRetry : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(System.currentTimeMillis())

    env
      .withHeader(headerCfg.headerMaxRetries, maxRetries).get
      .withHeader(headerCfg.headerRetryCount, retryCount).get
      .withHeader(headerCfg.headerRetryTimeout, retryTimeout).get
      .withHeader(headerCfg.headerFirstRetry, firstRetry).get
  }

  val validate : FlowProcessor.IntegrationStep = env => Try {

    val mandatoryHeader : String => Long = h =>
      env.header[Long](h) match {
        case None => throw new MissingHeaderException(h)
        case Some(l) => l
      }

    val maxRetries : Long = mandatoryHeader(headerCfg.headerMaxRetries)
    val retryCount : Long = mandatoryHeader(headerCfg.headerRetryCount)
    val retryTimeout : Long = mandatoryHeader(headerCfg.headerRetryTimeout)
    val firstRetry : Long = mandatoryHeader(headerCfg.headerFirstRetry)

    val remaining : FiniteDuration = (retryTimeout - (System.currentTimeMillis() - firstRetry)).millis
    log.logEnv(env, LogLevel.Debug, s"Retrying envelope [${env.id}] : [$retryCount / $maxRetries] [${remaining}] remaining")

    if (maxRetries > 0 && retryCount > maxRetries) {
      throw new RetryCountExceededException(maxRetries)
    }

    if (System.currentTimeMillis() - firstRetry > retryTimeout) {
      throw new RetryTimeoutException(firstRetry + retryTimeout)
    }

    if (env.header[String](headerCfg.headerRetryDestination).isEmpty) {
      throw new MissingRetryDestinationException(headerCfg.headerRetryDestination)
    }

    env
  }

  val flow : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] =
    Flow.fromGraph(FlowProcessor.fromFunction(name + ".header", log)(header))
      .via(FlowProcessor.fromFunction(name + ".validate", log)(validate))

}
