package blended.activemq.client.internal

import java.io.File

import akka.actor.ActorSystem
import blended.activemq.client.{ConnectionVerifier, RoundtripConnectionVerifier}
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue, SimpleIdAwareConnectionFactory}
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.{LoggingFreeSpec, LoggingFreeSpecLike}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.osgi.framework.BundleActivator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RequiresForkedJVM
class RoundtripConnectionVerifierSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
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

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "default").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def afterAll() : Unit = {
    broker.stop()
    broker.waitUntilStopped()
  }

  "The RoundtripConnectionVerifier should" - {

    "succeed upon a successfull request / response cycle" in {

      implicit val system : ActorSystem = actorSystem
      implicit val eCtxt : ExecutionContext = system.dispatcher

      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = id => FlowEnvelope(FlowMessage(FlowMessage.noProps), id),
        verify = _ => true,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip")
      )

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "activemq",
        provider = "succeed",
        clientId = "succeed",
        cf = new ActiveMQConnectionFactory("vm://roundtrip?create=false")
      )(actorSystem)

      val f = verifier.verifyConnection(ctCtxt)(cf)
      assert(Await.result(f, 5.seconds))
    }

    "fail if the response message could not be verified" in {

      implicit val system : ActorSystem = actorSystem
      implicit val eCtxt : ExecutionContext = system.dispatcher

      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = id => FlowEnvelope(FlowMessage(FlowMessage.noProps), id),
        verify = _ => false,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip")
      )

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "activemq",
        provider = "fail",
        clientId = "fail",
        cf = new ActiveMQConnectionFactory("vm://roundtrip?create=false")
      )(actorSystem)

      val f = verifier.verifyConnection(ctCtxt)(cf)
      assert(!Await.result(f, 5.seconds))
    }

    "stay unresolved if the connection to the broker did not succeed" in {

      implicit val system : ActorSystem = actorSystem
      implicit val eCtxt : ExecutionContext = system.dispatcher

      val ucf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "unresolved",
        clientId = "spec",
        cf = new ActiveMQConnectionFactory("vm://unresolved?create=false")
      )

      val verifier : ConnectionVerifier = new RoundtripConnectionVerifier(
        probeMsg = id => FlowEnvelope(FlowMessage("Hello Broker")(FlowMessage.noProps), id),
        verify = _ => false,
        requestDest = JmsQueue("roundtrip"),
        responseDest = JmsQueue("roundtrip")
      )

      val f = verifier.verifyConnection(ctCtxt)(ucf)
      Thread.sleep(5.seconds.toMillis)
      assert(!f.isCompleted)
    }
  }
}
