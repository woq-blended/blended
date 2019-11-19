package blended.jms.bridge.internal

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.RichTry._
import blended.util.logging.Logger
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.Matchers

import scala.concurrent.duration._

class JmsRetryRouterSpec extends TestKit(ActorSystem("RetryRouter"))
  with LoggingFreeSpecLike
  with Matchers {

  private[this] val log : Logger = Logger[JmsRetryRouterSpec]
  private[this] val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = "spec")
  private[this] val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

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
    eventDestName = "transactions",
    retryInterval = 3.seconds,
    headerCfg = headerCfg
  )

  "The JmsRetryRouter should" - {

    "throw a RetryCountExceededException if the max retry count is reached" in {

      val maxRetries : Long = 5L

      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg.copy(maxRetries = maxRetries),
        log = envLogger
      )

      intercept[RetryCountExceededException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerRetryCount, maxRetries).unwrap

        router.validate(router.header(env).get).unwrap
      }
    }

    "throw a RetryTimeoutException if the max retry timeout is reached" in {
      val timeout : Long = 100

      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg.copy(retryTimeout = (timeout / 2).millis),
        log = envLogger
      )

      intercept[RetryTimeoutException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - timeout).unwrap

        router.validate(router.header(env).get).unwrap
      }
    }

    "throw a MissingRetryDestinationException if the header is missing in the retry message" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = envLogger
      )

      intercept[MissingRetryDestinationException] {
        val env : FlowEnvelope = FlowEnvelope()
        router.validate(router.header(env).get).unwrap
      }
    }

    "increment the retry count by 1 in normal operations" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = envLogger
      )

      val env : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, "myQueue").unwrap

      val env1 : FlowEnvelope = router.validate(router.header(env).get).unwrap
      val env2 : FlowEnvelope = router.validate(router.header(env1).get).unwrap

      env2.header[Int](headerCfg.headerRetryCount) should be(Some(2))
    }

    "maintain the first retry timestamp in normal operations" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = envLogger
      )

      val env : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, "myQueue").unwrap

      val env1 : FlowEnvelope = router.validate(router.header(env).get).unwrap
      val env2 : FlowEnvelope = router.validate(router.header(env1).get).unwrap

      env2.header[Long](headerCfg.headerFirstRetry) should be(defined)
      env2.header[Long](headerCfg.headerFirstRetry) should be(env1.header[Long](headerCfg.headerFirstRetry))
    }
  }
}
