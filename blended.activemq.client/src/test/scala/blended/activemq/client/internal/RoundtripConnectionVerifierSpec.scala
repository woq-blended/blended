package blended.activemq.client.internal

import akka.actor.ActorSystem
import blended.activemq.client.{ConnectionVerifier, RoundtripConnectionVerifier}
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue, SimpleIdAwareConnectionFactory}
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class RoundtripConnectionVerifierSpec extends LoggingFreeSpec
  with Matchers
  with BeforeAndAfterAll {

  private val broker : BrokerService = {
    val b = new BrokerService()
    b.setBrokerName("roundtrip")
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)
    b.setDedicatedTaskRunner(true)

    b.start()
    b.waitUntilStarted()
    b
  }

  private implicit val system : ActorSystem = ActorSystem("roundtrip")
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  private val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
    vendor = "activemq",
    provider = "activemq",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory("vm://roundtrip?create=false")
  )

  override protected def afterAll(): Unit = {
    broker.stop()
    broker.waitUntilStopped()

    Await.result(system.terminate(), 5.seconds)
  }

  "The RoundtripConnectionVerifier should" - {

    "succeed upon a successfull request / response cycle" in {

      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = () => FlowEnvelope(FlowMessage("Hello Broker")(FlowMessage.noProps)),
        verify = env => true,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip"),
        headerConfig = FlowHeaderConfig.create(prefix = "App")
      )

      val f = verifier.verifyConnection(cf)
      assert(Await.result(f, 5.seconds))
    }

    "fail if the response message could not be verified" in {
      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = () => FlowEnvelope(FlowMessage("Hello Broker")(FlowMessage.noProps)),
        verify = env => false,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip"),
        headerConfig = FlowHeaderConfig.create(prefix = "App")
      )

      val f = verifier.verifyConnection(cf)
      assert(!Await.result(f, 5.seconds))
    }

    "stay unresolve if the connection to the broker did not succeed" in {

      val ucf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "unresolved",
        clientId = "spec",
        cf = new ActiveMQConnectionFactory("vm://unresolved?create=false")
      )

      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = () => FlowEnvelope(FlowMessage("Hello Broker")(FlowMessage.noProps)),
        verify = env => false,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip"),
        headerConfig = FlowHeaderConfig.create(prefix = "App")
      )

      val f = verifier.verifyConnection(ucf)
      Thread.sleep(5.seconds.toMillis)
      assert(!f.isCompleted)
    }
  }
}
