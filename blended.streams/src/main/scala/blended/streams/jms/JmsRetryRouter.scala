package blended.streams.jms

import java.text.SimpleDateFormat
import java.util.Date

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.{FlowHeaderConfig, FlowProcessor}
import blended.util.logging.LogLevel
import blended.util.logging.LogLevel.LogLevel

import scala.concurrent.duration._
import scala.util.Try

class JmsRetryException(msg : String) extends Exception(msg)

class RetryCountExceededException(n : Long)
  extends JmsRetryException(s"Maximum Retry [$n] count exceeded")

class RetryTimeoutException(t : Long)
  extends JmsRetryException(s"Retry timeout [${new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(t))}] exceeded")

class MissingRetryDestinationException(d : String)
  extends JmsRetryException(s"The retry destination header [$d] is missing in the message.")

class MissingRetryHeaderException(h : String)
  extends JmsRetryException(s"The envelope is missing the header ($h)")

class JmsRetryRouter(
  name : String,
  retryCfg : JmsRetryConfig,
  log : FlowEnvelopeLogger
) {

  private[this] val headerCfg : FlowHeaderConfig = retryCfg.headerCfg

  // make sure all headers are set without encountering an exception
  val header : FlowProcessor.IntegrationStep = env => Try {

    // Decide if we are already within the retryLoop
    val retrying : Boolean = env.header[String](headerCfg.headerRetrying).nonEmpty
    val maxRetries : Long = env.header[Long](headerCfg.headerMaxRetries).getOrElse(retryCfg.maxRetries)

    // In case we are not yet in the retry loop we will reset the retry counter in any case
    val retryCount : Long = if (retrying) {
      env.header[Long](headerCfg.headerRetryCount).getOrElse(0L) + 1
    } else {
      1L
    }

    val retryTimeout : Long = env.header[Long](headerCfg.headerRetryTimeout).getOrElse(retryCfg.retryTimeout.toMillis)
    val firstRetry : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(System.currentTimeMillis())

    env
      .withHeader(headerCfg.headerMaxRetries, maxRetries).get
      .withHeader(headerCfg.headerRetryCount, retryCount).get
      .withHeader(headerCfg.headerRetryTimeout, retryTimeout).get
      .withHeader(headerCfg.headerFirstRetry, firstRetry).get
      .withHeader(headerCfg.headerRetrying, "True").get
  }

  val validate : LogLevel => FlowProcessor.IntegrationStep = level => env => Try {

    val mandatoryHeader : String => Long = h =>
      env.header[Long](h) match {
        case None    => throw new MissingRetryHeaderException(h)
        case Some(l) => l
      }

    val maxRetries : Long = mandatoryHeader(headerCfg.headerMaxRetries)
    val retryCount : Long = mandatoryHeader(headerCfg.headerRetryCount)
    val retryTimeout : Long = mandatoryHeader(headerCfg.headerRetryTimeout)
    val firstRetry : Long = mandatoryHeader(headerCfg.headerFirstRetry)

    val remaining : FiniteDuration = (retryTimeout - (System.currentTimeMillis() - firstRetry)).millis
    log.logEnv(env, level, s"Retrying envelope [${env.id}] : [$retryCount / $maxRetries] [${remaining}] remaining", false)

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
      .via(FlowProcessor.fromFunction(name + ".validate", log)(validate(LogLevel.Debug)))

}
