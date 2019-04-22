package blended.streams.jms

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.Matchers

import scala.concurrent.duration._

class JmsRetryRouterSpec extends TestKit(ActorSystem("RetryRouter"))
  with LoggingFreeSpecLike
  with Matchers {

  private[this] val log : Logger = Logger[JmsRetryProcessorSpec]
  private[this] val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = "spec")

  private[this] val amqCf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
    vendor = "amq",
    provider = "amq",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory(s"vm://blended?create=false&jms.prefetchPolicy.queuePrefetch=10")
  )

  private[this] val retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    retryInterval = 3.seconds,
    headerCfg = headerCfg
  )

  "The JmsRetryRouter should" - {

    "throw a RetryCountExceededException if the max retry count is reached" in {

      val maxRetries : Long = 5L

      val router : JmsRetryRouter = new JmsRetryRouter(
        retryCfg = retryCfg.copy(maxRetries = maxRetries)
      )

      intercept[RetryCountExceededException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerRetryCount, maxRetries).get

        router.resolve(env).get
      }
    }

    "throw a RetryTimeoutException if the max retry timeout is reached" in {
      val  timeout : Long = 100

      val router : JmsRetryRouter = new JmsRetryRouter(
        retryCfg = retryCfg.copy(retryTimeout = (timeout / 2).millis)
      )

      intercept[RetryTimeoutException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - timeout).get

        router.resolve(env).get
      }
    }

    "throw a MissingRetryDestinationException if the header is missing in the retry message" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        retryCfg = retryCfg
      )

      intercept[MissingRetryDestinationException] {
        val env : FlowEnvelope = FlowEnvelope()
        router.resolve(env).get
      }
    }
  }
}
