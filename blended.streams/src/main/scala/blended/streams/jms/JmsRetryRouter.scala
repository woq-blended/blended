package blended.streams.jms

import java.text.SimpleDateFormat
import java.util.Date

import blended.streams.FlowProcessor
import blended.streams.transaction.FlowHeaderConfig

import scala.util.Try

class RetryCountExceededException(n : Long)
  extends Exception(s"Maximum Retry [$n] count exceeded")

class RetryTimeoutException(t : Long)
  extends Exception(s"Retry timeout [${new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(t))}] exceeded")

class MissingRetryDestinationException(d : String)
  extends Exception(s"The retry destination header [$d] is missing in the message.")

class JmsRetryRouter(
  retryCfg : JmsRetryConfig
) {

  private[this] val headerCfg : FlowHeaderConfig = retryCfg.headerCfg

  val resolve : FlowProcessor.IntegrationStep = env => Try {

    val maxRetries : Long = env.header[Long](headerCfg.headerMaxRetries).getOrElse(retryCfg.maxRetries)
    val retryCount : Long = env.header[Long](headerCfg.headerRetryCount).getOrElse(0L) + 1
    val retryTimeout : Long = env.header[Long](headerCfg.headerRetryTimeout).getOrElse(retryCfg.retryTimeout.toMillis)
    val firstRetry : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(System.currentTimeMillis())

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
      .withHeader(headerCfg.headerMaxRetries, maxRetries).get
      .withHeader(headerCfg.headerRetryCount, retryCount).get
      .withHeader(headerCfg.headerFirstRetry, firstRetry).get
  }
}
