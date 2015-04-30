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

package de.wayofquality.blended.akka

import akka.actor._
import akka.event.LoggingReceive
import akka.testkit.{TestActorRef, TestLatch, TestProbe}
import com.typesafe.config.Config
import de.wayofquality.blended.akka.protocol._
import de.wayofquality.blended.testsupport.TestActorSys
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.util.{Success, Try}

object OSGIActorDummyPublisher {
  def apply(actorConfig: OSGIActorConfig) = new OSGIActorDummyPublisher(actorConfig)
}

class OSGIActorDummyPublisher(actorConfig: OSGIActorConfig) extends OSGIActor(actorConfig) with ProductionEventSource {

  def receive = LoggingReceive { eventSourceReceive }
}

//----------

object OSGIDummyListener {
  def apply(cfg : OSGIActorConfig) = new OSGIDummyListener(cfg) with OSGIEventSourceListener
}

class OSGIDummyListener(cfg: OSGIActorConfig) extends OSGIActor(cfg) { this : OSGIEventSourceListener =>

  implicit val actorSys = context.system
  val latch = TestLatch(1)
  
  val publisherName = bundleActorConfig.getString("listener.publisher")
  
  def receive =  testing orElse eventListenerReceive(publisherName)

  def testing : Receive = {
    case "Andreas" => latch.countDown()
  }
}

class OSGIEventSourceListenerSpec extends WordSpec with Matchers {

  "The OSGI Event source listener" should {

    "subscribe to a publisher bundle if it already exists in the actor system" in new TestActorSys with TestSetup with MockitoSugar {

      import scala.concurrent.duration._

      system.eventStream.subscribe(testActor, classOf[BundleActorStarted])

      val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher"))), "publisher")
      val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener"))), "listener")

      // We need to wait for the Actor bundle to finish it's initialization
      fishForMessage() {
        case BundleActorStarted(s) if s == "listener" => true
        case _ => false
      }

      //TODO: This is a test only hack to give the listener some time to finish its registration
      Thread.sleep(100)

      publisher ! SendEvent("Andreas")

      val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
      Await.ready(latch, 1.second)
      latch.isOpen should be (true)
    }
  }

  "start referring to the dlc when the publisher is unavailbale" in new TestActorSys with TestSetup with MockitoSugar {

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener"))), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorStarted])

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(system.deadLetters)
  }

  "subscribe to the publisher when it becomes available" in new TestActorSys with TestSetup with MockitoSugar {

    import scala.concurrent.duration._

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener"))), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorStarted])

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher"))), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    publisher ! SendEvent("Andreas")
    val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
    Await.ready(latch, 1.second)
    latch.isOpen should be (true)
  }

  "fallback to system.dlc when the publisher becomes unavailable" in new TestActorSys with TestSetup with MockitoSugar {

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener"))), "listener")

    system.eventStream.subscribe(testActor, classOf[BundleActorStarted])

    // We need to wait for the Actor bundle to finish it's initialization
    fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher"))), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    val watcher = new TestProbe(system)
    watcher.watch(publisher)

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    system.stop(publisher)
    watcher.expectMsgType[Terminated]

    listenerReal.publisher should be (system.deadLetters)
  }
}
