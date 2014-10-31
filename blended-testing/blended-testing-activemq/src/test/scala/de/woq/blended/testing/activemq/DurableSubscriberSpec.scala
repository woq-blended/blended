/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.woq.blended.testing.activemq

import java.net.URI
import java.util.concurrent.CountDownLatch
import javax.jms.ConnectionFactory

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import de.woq.blended.itestsupport.jms.{TextMessageFactory, JMSConnectorActor}
import de.woq.blended.itestsupport.jms.protocol._
import de.woq.blended.testsupport.TestActorSys
import de.woq.blended.util.protocol.{CounterInfo, TrackingCounter}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{BrokerService, TransportConnector}
import org.apache.activemq.network.{DiscoveryNetworkConnector, NetworkConnector}
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

import scala.concurrent.Future

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
 *
 * The test is based on ActiveMQ 5.10
 */

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
    def connect(connector: ActorRef, clientId: String)(implicit testkit : TestKit) : Unit = {
      connector.tell(Connect(clientId), testkit.testActor)
      testkit.expectMsg(Right(Connected(clientId)))
    }

    // Disconnecting from a broker and verifying it
    def disconnect(connector: ActorRef)(implicit testkit : TestKit) : Unit = {
      connector.tell(Disconnect, testkit.testActor)
      testkit.expectMsg(Right(Disconnected))
    }

    // Create a producer and optionally link it to a TrackingCounter
    def producer(connector: ActorRef, dest: String, counter: Option[ActorRef] = None)(implicit testkit : TestKit) : Future[ActorRef] = {
      implicit val ctxt = testkit.system.dispatcher
      (connector ? CreateProducer(dest, counter)).mapTo[ProducerActor].map(_.producer)
    }

    // Create a durable sbscriber and optionally link it to a tracking counter
    def durableSubscriber(connector: ActorRef, dest: String, subscriberName: String, counter: Option[ActorRef])(implicit testkit : TestKit) : Future[ActorRef] = {
      implicit val ctxt = testkit.system.dispatcher
      (connector ? CreateDurableSubscriber(dest, subscriberName, counter)).mapTo[ConsumerActor].map(_.consumer)
    }

    // Produce a batch of messages for a given Producer
    def produceMessageBatch(producer: ActorRef)(implicit testkit: TestKit) : Future[Any] = {

      implicit val ctxt = testkit.system.dispatcher

      (producer ? ProduceMessage(
        msgFactory = messageFactory,
        content = Some(s"${System.currentTimeMillis}"),
        count = numMessages
      ))
    }

    // produce and consume some messages and check the message counts
    def produceAndConsume(
      consumerConnector : ActorRef,
      producerConnector : ActorRef,
      dest: String,
      subscribername: String
    )(implicit testkit: TestKit) : Unit = {

      implicit val ctxt = testkit.system.dispatcher
      implicit val system = testkit.system

      val latch = new CountDownLatch(1)

      // Create TrackingCounters for produced and consumed messages reporting back to the testActor
      val consumedCounter = TestActorRef(Props(TrackingCounter(1.seconds, testkit.testActor)))
      val producedCounter = TestActorRef(Props(TrackingCounter(1.seconds, testkit.testActor)))

      // First create a Durable Subscriber, then create a Producer and produce a message batch
      (for {
        c <- durableSubscriber(consumerConnector, destination, subscribername, Some(consumedCounter)).mapTo[ActorRef]
        p <- producer(producerConnector, destination, Some(producedCounter)).mapTo[ActorRef]
        msg <- produceMessageBatch(p)
      } yield msg).onSuccess { case _ =>
        // We should have a complete message batch produced and consumed
        testkit.fishForMessage(5.seconds) {
          case info: CounterInfo => info.count == numMessages
          case _ => false
        }

        testkit.fishForMessage(5.seconds) {
          case info: CounterInfo => info.count == numMessages
          case _ => false
        }

        latch.countDown()
      }

      latch.await(5, SECONDS)
    }

    "do stuff" in new TestActorSys {

      implicit val kit = this
      implicit val ctxt = system.dispatcher

      val connA = jmsConnector("broker1")
      connect(connA, "clientA")

      produceAndConsume(connA, connA, destination, "testSub")

      disconnect(connA)
    }
  }
}
