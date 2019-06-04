package blended.jms.bridge.internal

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

  private[this] val log : Logger = Logger[JmsRetryRouterSpec]
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
        log = log
      )

      intercept[RetryCountExceededException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerRetryCount, maxRetries).get

        router.validate(router.header(env).get).get
      }
    }

    "throw a RetryTimeoutException if the max retry timeout is reached" in {
      val timeout : Long = 100

      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg.copy(retryTimeout = (timeout / 2).millis),
        log = log
      )

      intercept[RetryTimeoutException] {
        val env : FlowEnvelope = FlowEnvelope()
          .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - timeout).get

        router.validate(router.header(env).get).get
      }
    }

    "throw a MissingRetryDestinationException if the header is missing in the retry message" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = log
      )

      intercept[MissingRetryDestinationException] {
        val env : FlowEnvelope = FlowEnvelope()
        router.validate(router.header(env).get).get
      }
    }

    "increment the retry count by 1 in normal operations" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = log
      )

      val env : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, "myQueue").get

      val env1 : FlowEnvelope = router.validate(router.header(env).get).get
      val env2 : FlowEnvelope = router.validate(router.header(env1).get).get

      env2.header[Int](headerCfg.headerRetryCount) should be(Some(2))
    }

    "maintain the first retry timestamp in normal operations" in {
      val router : JmsRetryRouter = new JmsRetryRouter(
        name = "spec",
        retryCfg = retryCfg,
        log = log
      )

      val env : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, "myQueue").get

      val env1 : FlowEnvelope = router.validate(router.header(env).get).get
      val env2 : FlowEnvelope = router.validate(router.header(env1).get).get

      env2.header[Long](headerCfg.headerFirstRetry) should be(defined)
      env2.header[Long](headerCfg.headerFirstRetry) should be(env1.header[Long](headerCfg.headerFirstRetry))
    }
  }
}
