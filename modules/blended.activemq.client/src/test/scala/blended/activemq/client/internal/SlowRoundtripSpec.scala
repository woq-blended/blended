package blended.activemq.client.internal

import java.io.File
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.activemq.client.{ConnectionVerifierFactory, RoundtripConnectionVerifier, VerificationFailedHandler}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils._
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import domino.DominoActivator
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.osgi.framework.BundleActivator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RequiresForkedJVM
class SlowRoundtripSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  private val brokerName : String = "slow"
  private val log : Logger = Logger[SlowRoundtripSpec]

  private val verifyRequest : String = "verify"
  private val verifyRespond : String = "verified"

  private val vendor : String = "activemq"
  private val provider : String = "conn1"

  private val broker : BrokerService = {
    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)
    b.setDedicatedTaskRunner(true)

    b.start()
    b.waitUntilStarted()
    b
  }


  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    broker.stop()
    broker.waitUntilStopped()
  }

  class SimpleResponder(system : ActorSystem) {

    implicit val actorSys : ActorSystem = system
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val simpleCf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
      vendor = "activemq", provider = "spec",
      clientId = "spec",
      cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false"),
      minReconnect = 5.seconds
    )

    def respond() : Unit = {

      log.info("Trying to receive verification request")

      val verifyRec : Collector[FlowEnvelope] = receiveMessages(
        headerCfg = headerCfg,
        cf = simpleCf,
        dest = JmsQueue(verifyRequest),
        log = envLogger(log),
        listener = 1,
        minMessageDelay = None,
        selector = None,
        completeOn = Some(_.nonEmpty),
        timeout = Some(3.seconds)
      )

      val verifyMsg : FlowEnvelope = Await.result(verifyRec.result, 4.seconds).headOption.get

      log.info("sending verification response")
      sendMessages(
        producerSettings = JmsProducerSettings(
          log = envLogger(log),
          headerCfg = headerCfg,
          connectionFactory = simpleCf,
          jmsDestination = Some(JmsQueue("verified"))
        ),
        log = envLogger(log),
        timeout = 10.seconds,
        verifyMsg
      )
    }
  }

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "slow").getAbsolutePath()

  private var failed : List[String] = List.empty
  private val verifyCounter : AtomicInteger = new AtomicInteger(0)

  private class SlowRoundtripActivator extends DominoActivator {

    private val firstTry : AtomicBoolean = new AtomicBoolean(true)

    whenBundleActive {
      whenServicePresent[ActorSystem] { system =>
        implicit val actorSys : ActorSystem = system

        val responder : SimpleResponder = new SimpleResponder(system)
        val slowFactory : ConnectionVerifierFactory = () => new RoundtripConnectionVerifier(
          probeMsg = id => FlowEnvelope(FlowMessage(FlowMessage.noProps), id),
          verify = _ => true,
          requestDest = JmsQueue(verifyRequest),
          responseDest = JmsQueue(verifyRespond),
          retryInterval = 5.seconds,
          receiveTimeout = 5.seconds
        ) {

          override protected def probe(ctCtxt: ContainerContext)(cf: IdAwareConnectionFactory): Unit = {
            verifyCounter.incrementAndGet()
            if (firstTry.get()) {

              val probe: TestProbe = TestProbe()
              system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
              system.eventStream.publish(QueryConnectionState(vendor, provider))

              probe.fishForMessage(timeout, "Waiting for first connection") {
                case evt: ConnectionStateChanged => evt.state.status == Connected
              }

              super.probe(ctCtxt)(cf)
              system.eventStream.publish(MaxKeepAliveExceeded(vendor, provider))

              probe.fishForMessage(timeout, "Waiting for disconnect") {
                case evt: ConnectionStateChanged => evt.state.status == Disconnected
              }

              system.stop(probe.ref)
              responder.respond()

              firstTry.set(false)
            } else {
              super.probe(ctCtxt)(cf)
              responder.respond()
            }
          }
        }

        val slowHandler : VerificationFailedHandler = (cf: IdAwareConnectionFactory) => {
          failed = s"${cf.vendor}:${cf.provider}" :: failed
        }

        slowFactory.providesService[ConnectionVerifierFactory]("name" -> "slow")
        slowHandler.providesService[VerificationFailedHandler]("name" -> "slow")
      }
    }
  }

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.client" -> new AmqClientActivator(),
    "slow" -> new SlowRoundtripActivator
  )

  "The ActiveMQ Client Activator should" - {

    "register a connection factory after the underlying connection factory has been restarted due to failed pings" in {

      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      probe.fishForMessage(5.seconds, "Waiting for second connected event"){
        case evt : ConnectionStateChanged =>
          evt.state.status == Connected
      }

      // The service will be available after the verifier has finally verified the connection
      // It should still succeed after the connection restart
      mandatoryService[IdAwareConnectionFactory](registry, filter = Some("(&(vendor=activemq)(provider=conn1))"), timeout = 30.seconds)

      failed should be (empty)
      assert(verifyCounter.get() > 1)
    }
  }
}
