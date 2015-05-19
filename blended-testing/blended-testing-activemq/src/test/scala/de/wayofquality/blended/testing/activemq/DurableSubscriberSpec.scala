/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.testing.activemq

import java.net.URI
import java.util.UUID

/**
 * This specification shall help to investigate the duplicate delivery of messages for durable subscribers
 * within a network of brokers. The problem has been posted on the ActiveMQ mailing list on Oct. 18th 2014
 * and was described as follows:
 *
 * Suppose you have a network of brokers consisting of two members discovering each other via multicast.
 * The network bridge is set up using conduit subscriptions. Now assume that we have a durable subscriber
 * named "S" that connects to the network of brokers using a failover uri pointing to both brokers.
 *
 * First, the subscriber connects to Broker A. It will consume all messages published to either Broker A or B.
 * Now the subscriber disconnects and stays offline for a bit, then it reconnects to Broker B. Now it will pick
 * up all messages that have been published while it was offline.
 *
 * Let's say then 10 messages are published. All is well as the subscriber consumes those messages.
 * If the subscriber then disconnects and reconnects to Broker A, these 10 messages will be consumed
 * again.
 *
 * According to Tim Bain on Oct., 20th 2014 this indicates a bug rather than a missing feature in ActiveMQ
 * and this Spec shall pinpoint the behavior.
 * *
 * The test is based on ActiveMQ 5.10
 *
 * Observations:
 * -------------
 * Depending on when the durable subscriber is known to the members of the NWOB, messages can be either lost
 * or delivered repeatedly (see the last 2 test cases). Message loss can happen, if the DS has only connected
 * to one broker so far. If the DS then disconnects and after a while reconnects to the other broker it wasn't
 * connected to so far, it will not see the messages that have been produced while it was offline (it will see
 * those messages after reconnecting to broker 1).
 *
 * Dupilcate delivery will happen if the DS was already connected to both brokers. From the broker's perspective
 * it seems that those DS are handled as two distinct subscribers, so effectively all messages that are published
 * will eventually be delivered to both subscribers.
 */

/************************************************************************************************/

/**
 * Encapsulate the setup of an ActiveMQ broker with a given name and connector URI. The connector
 * will be published via a multicast discovery URL, so that the brokers can connect to each other.
 */
trait ActiveMQBroker {
  val brokerName : String
  val connectorUri : String

  val discoveryAddress = "multicast://default"

  def connectionFactory : ConnectionFactory =
    new ActiveMQConnectionFactory(connectorUri)

  private def connector(uri: String) : TransportConnector = {
    val result = new TransportConnector()

    result.setUri(new URI(uri))
    result.setDiscoveryUri(new URI(discoveryAddress))
    result
  }

  private def networkConnector : NetworkConnector = {
    val result = new DiscoveryNetworkConnector()

    result.setUri(new URI(discoveryAddress))
    result.setConduitSubscriptions(true)

    result
  }

  lazy val brokerService : BrokerService = {
    val broker = new BrokerService

    broker.setBrokerName(brokerName)
    broker.setPersistenceAdapter(new MemoryPersistenceAdapter())
    broker.setUseJmx(false)

    broker.addConnector(connector(connectorUri))
    broker.addNetworkConnector(networkConnector)

    broker
  }
}

/**
 * Convenience helper object for creating brokers.
 */
object ActiveMQBroker {
  def apply(name: String, uri: String) = new ActiveMQBroker {
    override val brokerName = name
    override val connectorUri = uri
  }
}

class DurableSubscriberSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger(classOf[DurableSubscriberSpec])

  /**
   * The brokers under test. These will interconnect using a discovery URI.
   */
  val brokers = Map(
    "broker1" -> ActiveMQBroker("broker1", "tcp://0.0.0.0:44444"),
    "broker2" -> ActiveMQBroker("broker2", "tcp://0.0.0.0:44445")
  )

  // start all brokers
  override protected def beforeAll(): Unit = {
    brokers.values.foreach { broker =>
      log info s"Starting broker [${broker.brokerService.getBrokerName}]"
      broker.brokerService.start()
      broker.brokerService.waitUntilStarted()
    }
  }

  // shut down all brokers
  override protected def afterAll(): Unit = {
    brokers.values.foreach { broker =>
      log info s"Stopping broker [${broker.brokerService.getBrokerName}]"
      broker.brokerService.stop()
      broker.brokerService.waitUntilStopped()
    }
  }

  "An ActiveMQ network of brokers" should {

    // A timeout for the ask pattern
    implicit val timeout = Timeout(3.seconds)

    // We simply send some Text Messages
    val messageFactory = new TextMessageFactory()

    // This is the number of messages we send in one batch
    val numMessages = 10

    // This is the destination used for testing
    val destination = "topic:test"

    // A JMS Connector for creating Producers & Consumers
    def jmsConnector(brokerName: String)(implicit system: ActorSystem) =
      TestActorRef(Props(JMSConnectorActor(brokers(brokerName).connectionFactory)))

    // Connecting to a broker and verifying it
    def connect(connector: ActorRef, clientId: String)(implicit testkit: TestKit): Unit = {
      connector.tell(Connect(clientId), testkit.testActor)
      testkit.expectMsg(Right(Connected(clientId)))
    }

    // Disconnecting from a broker and verifying it
    def disconnect(connector: ActorRef)(implicit testkit: TestKit): Unit = {
      connector.tell(Disconnect, testkit.testActor)
      testkit.expectMsg(Right(Disconnected))
    }

    // Create a producer and optionally link it to a TrackingCounter
    def producer(connector: ActorRef, dest: String, counter: Option[ActorRef] = None)(implicit testkit: TestKit): Future[ActorRef] = {
      implicit val ctxt = testkit.system.dispatcher
      (connector ? CreateProducer(dest, counter)).mapTo[ProducerActor].map(_.producer)
    }

    // Create a durable sbscriber and optionally link it to a tracking counter
    def durableSubscriber(connector: ActorRef, dest: String, subscriberName: String, counter: Option[ActorRef] = None)(implicit testkit: TestKit): Future[ActorRef] = {
      implicit val ctxt = testkit.system.dispatcher
      (connector ? CreateDurableSubscriber(dest, subscriberName, counter)).mapTo[ConsumerActor].map(_.consumer)
    }

    // Produce a batch of messages for a given Producer
    def produceMessageBatch(producer: ActorRef)(implicit testkit: TestKit): Future[Any] = {

      implicit val ctxt = testkit.system.dispatcher

      (producer ? ProduceMessage(
        msgFactory = messageFactory,
        content = Some(s"${System.currentTimeMillis}"),
        count = numMessages
      ))
    }

    def checkCounter(probe: TestProbe, count: Int)(implicit testkit: TestKit): Unit = {
      probe.fishForMessage() {
        case info: CounterInfo => {
          log.info(s"Received counter info of [${info.count}], expecting [${count}]")
          info.count == count
        }
        case _ => false
      }
    }

    def produceAndCount(producerConnector: ActorRef, dest: String)(implicit testkit: TestKit): Future[TestProbe] = {
      implicit val ctxt = testkit.system.dispatcher
      implicit val system = testkit.system

      val probe = TestProbe()

      // Create a TrackingCounter for the produced messages reporting back to the probe
      val producedCounter = TestActorRef(Props(TrackingCounter(1.seconds, probe.ref)))

      for {
        p <- producer(producerConnector, destination, Some(producedCounter)).mapTo[ActorRef]
        msg <- produceMessageBatch(p)
      } yield probe
    }

    def consumeAndCount(consumerConnector: ActorRef, dest: String, subscriberName: String)(implicit testkit: TestKit): Future[TestProbe] = {
      implicit val ctxt = testkit.system.dispatcher
      implicit val system = testkit.system

      log info s"Setting up consumer [${subscriberName}]"

      val probe = TestProbe()

      // Create a TrackingCounter for the consumed messages reporting back to the probe
      val consumedCounter = TestActorRef(Props(TrackingCounter(1.seconds, probe.ref)))

      for {
        s <- durableSubscriber(consumerConnector, destination, subscriberName, Some(consumedCounter)).mapTo[ActorRef]
      } yield probe

    }

    // produce and consume some messages and check the message counts
    def produceAndConsume(
      consumerConnector: ActorRef,
      producerConnector: ActorRef,
      dest: String,
      subscribername: String
    )(implicit testkit: TestKit): Future[(TestProbe, TestProbe)] = {

      implicit val ctxt = testkit.system.dispatcher
      implicit val system = testkit.system

      for {
        consumerProbe <- consumeAndCount(consumerConnector, dest, subscribername)
        producerProbe <- produceAndCount(producerConnector, dest)
      } yield (consumerProbe, producerProbe)
    }

    // Provide a frame for running a test and make sure thaht everything is cleaned up afterwards
    def runNWOBTest( f : ((ActorRef, ActorRef) => Unit))(implicit testkit : TestKit): Boolean = {

      implicit val system = testkit.system

      val connA = jmsConnector("broker1")
      val connB = jmsConnector("broker2")

      try {
        connect(connA, "myclient")
        connect(connB, "myclient")
        f(connA, connB)
        true
      } catch {
        case t : Throwable =>
          testkit.system.log.error(s"Exception caught executing test [${t.getStackTraceString}]")
          false
      } finally {
        disconnect(connA)
        disconnect(connB)
      }
    }

    "consume messages that have been produced on the same broker instance" in new TestActorSys {

      /* Simply create a producer and a consumer on the same broker instance and check that an
       * equal number of messages has been produced / consumed.
       */
      log.info("=" * 80)

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      runNWOBTest { (connA, connB) =>
        val subscriberName = UUID.randomUUID.toString

        val (cp, pp) = Await.result(produceAndConsume(connA, connA, destination, subscriberName), 10.seconds)

        checkCounter(cp, numMessages)
        checkCounter(pp, numMessages)
      } should be (true)
    }

    "consume messages that have been produced on the peer broker instance" in new TestActorSys {

      /* Simply create a producer on broker1 and a consumer on broker2 and check that an
       * equal number of messages has been produced / consumed.
       */
      log.info("=" * 80)

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      runNWOBTest { (connA, connB) =>
        val subscriberName = UUID.randomUUID.toString

        val (cp, pp) = Await.result(produceAndConsume(connA, connB, destination, subscriberName), 10.seconds)

        checkCounter(cp, numMessages)
        checkCounter(pp, numMessages)
      } should be (true)
    }

    "consume messages from the same broker that have been produced while the durable subscriber was offline" in new TestActorSys {

      /* 1. Create a durable subscriber on broker1 and immediately stop it
       * 2. Create a producer on broker 1 and produce n messages
       * 3. Reconnect the durable subscriber
       * 4. Check that an equal number of messages has been produced / consumed
       */

      log.info("=" * 80)

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      runNWOBTest { (connA, connB) =>
        val subscriberName = UUID.randomUUID.toString

        val stopProbe = TestProbe()
        system.eventStream.subscribe(stopProbe.ref, classOf[ConsumerStopped])

        durableSubscriber(connA, destination, subscriberName).map(_ ! StopConsumer)
        stopProbe.expectMsg(ConsumerStopped(destination))

        val pp = Await.result(produceAndCount(connA, destination), 10.seconds)
        checkCounter(pp, numMessages)

        val cp = Await.result(consumeAndCount(connA, destination, subscriberName), 10.seconds)
        checkCounter(cp, numMessages)
      } should be (true)
    }

    "consume messages from the peer broker that have been produced while the subscriber was offline" in new TestActorSys {

      /* 1. Create a durable subscriber on broker1 and immediately stop it
       * 2. Create a producer on broker 1 and produce n messages
       * 3. Reconnect the durable subscriber on broker 2
       * 4. Check that an equal number of messages has been produced / consumed
       */

      log.info("=" * 80)

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      runNWOBTest { (connA, connB) =>
        val subscriberName = UUID.randomUUID.toString

        val stopProbe = TestProbe()
        system.eventStream.subscribe(stopProbe.ref, classOf[ConsumerStopped])

        durableSubscriber(connA, destination, subscriberName).map(_ ! StopConsumer)
        stopProbe.expectMsg(ConsumerStopped(destination))

        val pp = Await.result(produceAndCount(connA, destination), 10.seconds)
        checkCounter(pp, numMessages)

        val cp = Await.result(consumeAndCount(connB, destination, subscriberName), 10.seconds)
        checkCounter(cp, numMessages)
      } should be (true)
    }

    "avoid duplicate delivery of messages" in new TestActorSys {

      /* 1. Create a durable subscriber on broker 1 and immediate stop it.
       * 2. Reconnect the subscriber to broker 2
       * 3. Create a producer on broke 1 and produce n messages
       * 4. The connected subscriber should have received n messages
       * 5. Stop the subscriber and reconnect to broker 1
       * 6. The reconnected subscriber should have received no more messages.
       */

      log.info("=" * 80)

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      runNWOBTest { (connA, connB) =>
        val subscriberName = UUID.randomUUID.toString

        val stopProbe = TestProbe()
        system.eventStream.subscribe(stopProbe.ref, classOf[ConsumerStopped])

        durableSubscriber(connA, destination, subscriberName).map(_ ! StopConsumer)
        stopProbe.expectMsg(ConsumerStopped(destination))

        val cp1 = Await.result(consumeAndCount(connB, destination, subscriberName), 10.seconds)
        val pp = Await.result(produceAndCount(connA, destination), 10.seconds)

        checkCounter(pp, numMessages)
        checkCounter(cp1, numMessages)

        stopProbe.fishForMessage() {
          case s : ConsumerStopped => true
          case _ => false
        }

        val cp2 = Await.result(consumeAndCount(connA, destination, subscriberName), 10.seconds)
        checkCounter(cp2, 0)
      } should be (true)
    }

  }
}
