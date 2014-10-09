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

package de.woq.blended.itestsupport.jms

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
import akka.util.Timeout
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.ask
import scala.concurrent.duration._

import de.woq.blended.util.protocol._
import de.woq.blended.itestsupport.jms.protocol._

import scala.concurrent.Await
import scala.util.Random

class JMSConnectorActorSpec extends TestActorSys("JMS")
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  val BROKER_NAME = "blended"
  var broker : Option[BrokerService] = None

  val cf = new ActiveMQConnectionFactory("vm://blended")

  "The JMSConnectorActor" should {

    "Allow to (dis)connect via the underlying JMS connection factory" in {

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)
      disconnect(connector)
    }

    "Allow to create a producer" in {
      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      connector ! CreateProducer("queue:test")
      expectMsgAllClassOf(classOf[ProducerActor])

      disconnect(connector)
    }

    "Allow to create a consumer" in {

      implicit val timeout = Timeout(3.seconds)

      system.eventStream.subscribe(testActor, classOf[ConsumerStopped])

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      val consumer =
        Await.result((connector ? CreateConsumer("queue:test")).mapTo[ConsumerActor], 1.second)

      consumer.consumer ! StopConsumer
      expectMsg(ConsumerStopped("queue:test"))

      disconnect(connector)
    }

    "Allow to create a durable subscriber" in {
      implicit val timeout = Timeout(3.seconds)

      system.eventStream.subscribe(testActor, classOf[ConsumerStopped])

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      connect(connector)

      val consumer =
        Await.result((connector ? CreateDurableSubscriber("topic:test", "test")).mapTo[ConsumerActor], 1.second)

      consumer.consumer ! StopConsumer
      expectMsg(ConsumerStopped("topic:test"))

      disconnect(connector)
    }

    "Count the number of produced messages" in {
      implicit val timeout = Timeout(3.seconds)

      val NUM_MSG = new Random(System.currentTimeMillis).nextInt(50) + 50

      val connector = TestActorRef(Props(JMSConnectorActor(cf)))
      val counter = TestActorRef(Props(TrackingCounter(1.second, testActor)))

      connect(connector)

      val producerActor =
        Await.result((connector ? CreateProducer("topic:test", Some(counter))).mapTo[ProducerActor], 1.second)

      producerActor.producer ! ProduceMessage(
        msgFactory = new TextMessageFactory,
        content = Some("test"),
        count = NUM_MSG
      )

      fishForMessage() {
        case info: CounterInfo =>
          info.count == NUM_MSG
        case _ => false
      }

      disconnect(connector)
    }
  }

  private def connect(connector: ActorRef) {
    connector ! Connect("test")
    expectMsg(Connected("test"))
  }

  private def disconnect(connector: ActorRef) {
    connector ! Disconnect
    expectMsg(Disconnected)
  }
  override protected def beforeAll() {
    super.beforeAll()

    broker = {
      val b = new BrokerService()
      b.setBrokerName(BROKER_NAME)
      b.setPersistent(false)
      b.setPersistenceAdapter(new MemoryPersistenceAdapter)

      b.start()
      b.waitUntilStarted()

      Some(b)
    }
  }

  override protected def afterAll() {
    super.afterAll()

    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
    broker = None
  }
}
