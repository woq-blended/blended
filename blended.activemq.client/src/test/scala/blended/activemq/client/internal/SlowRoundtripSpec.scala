package blended.activemq.client.internal

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import blended.activemq.client.{ConnectionVerifier, ConnectionVerifierFactory, RoundtripConnectionVerifier, VerificationFailedHandler}
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils._
import blended.streams.FlowHeaderConfig
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
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
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@RequiresForkedJVM
class SlowRoundtripSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")
  private val brokerName : String = "slow"
  private val log : Logger = Logger[SlowRoundtripSpec]

  private val verifyRequest : String = "verify"
  private val verifyRespond : String = "verified"

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

  override protected def afterAll(): Unit = {
    broker.stop()
    broker.waitUntilStopped()
  }

  class SimpleResponder(system : ActorSystem) {

    val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

    implicit val actorSys : ActorSystem = system
    implicit val materializer = ActorMaterializer()
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
        log = envLogger,
        listener = 1,
        minMessageDelay = None,
        selector = None,
        completeOn = Some(_.size > 0),
        timeout = Some(3.seconds)
      )

      val verifyMsg : FlowEnvelope = Await.result(verifyRec.result, 4.seconds).headOption.get

      log.info("sending verification response")
      sendMessages(
        producerSettings = JmsProducerSettings(
          log = envLogger,
          headerCfg = headerCfg,
          connectionFactory = simpleCf,
          jmsDestination = Some(JmsQueue("verified"))
        ),
        log = envLogger,
        verifyMsg
      )
    }
  }

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "slow").getAbsolutePath()

  private var failed : List[String] = List.empty

  private class SlowRoundtripActivator extends DominoActivator {

    private val firstTry : AtomicBoolean = new AtomicBoolean(true)

    whenBundleActive {

      whenServicePresent[ActorSystem] { system =>
        implicit val actorSys : ActorSystem = system

        val responder : SimpleResponder = new SimpleResponder(system)
        val slowFactory : ConnectionVerifierFactory = new ConnectionVerifierFactory {

          override def createConnectionVerifier(): ConnectionVerifier = new RoundtripConnectionVerifier(
            probeMsg = () => FlowEnvelope(),
            verify = _ => true,
            requestDest = JmsQueue(verifyRequest),
            responseDest = JmsQueue(verifyRespond),
            headerConfig = headerCfg,
            retryInterval = 5.seconds,
            receiveTimeout = 5.seconds
          ) {
            override protected def waitForResponse(cf: IdAwareConnectionFactory, id: String): Unit = {
              if (firstTry.get()) {
                implicit val eCtxt : ExecutionContext = system.dispatcher

                akka.pattern.after[Unit](500.millis, system.scheduler)( Future {
                  system.eventStream.publish(MaxKeepAliveExceeded("activemq", "conn1"))
                })

                akka.pattern.after[Unit](1.second, system.scheduler)(Future {
                  responder.respond()
                })

                firstTry.set(false)
              } else {
                responder.respond()
              }
              super.waitForResponse(cf, id)
            }
          }
        }

        val slowHandler : VerificationFailedHandler = new VerificationFailedHandler {
          override def verificationFailed(cf: IdAwareConnectionFactory): Unit = {
            failed = (s"${cf.vendor}:${cf.provider}") :: failed
          }
        }

        slowFactory.providesService[ConnectionVerifierFactory]("name" -> "slow")
        slowHandler.providesService[VerificationFailedHandler]("name" -> "slow")
      }
    }
  }

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "slow" -> new SlowRoundtripActivator,
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.client" -> new AmqClientActivator()
  )

  "The ActiveMQ Client Activator should" - {

    "register a connection factory after the underlying connection factory has been restarted due to failed pings" in {

      implicit val to : FiniteDuration = 30.seconds

      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      // Ensure the underlying Jms connection is there
      system.eventStream.publish(QueryConnectionState("activemq", "conn1"))
      probe.fishForMessage(3.seconds, "Waiting for connected event"){
        case evt : ConnectionStateChanged =>
          println(evt)
          evt.state.status.toString == Connected.toString
      }

      // Make sure the underlying connection factory reconnects
      probe.fishForMessage(3.seconds, "Waiting for disconnected event"){
        case evt : ConnectionStateChanged =>
          println(evt)
          evt.state.status == Disconnected
      }

      probe.fishForMessage(5.seconds, "Waiting for second connected event"){
        case evt : ConnectionStateChanged =>
          println(evt)
          evt.state.status == Connected
      }

      // The service will be available after the verifier has finally verified the connection
      // It should still succeed after the connection restart
      mandatoryService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=conn1))"))

      failed should be (empty)
    }
  }
}
