/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.system

import org.scalatest.{Matchers, WordSpec}
import org.osgi.framework.BundleContext
import akka.actor._
import de.woq.osgi.java.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import de.woq.osgi.akka.system.internal.OSGIFacade
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.protocol._
import akka.testkit.{TestLatch, TestActorRef}
import scala.concurrent.Await

object OSGIActorDummyPublisher {
  def apply()(implicit bundleContext: BundleContext) = new OSGIActorDummyPublisher() with OSGIActor
}

class OSGIActorDummyPublisher extends Actor with ActorLogging with ProductionEventSource { this : OSGIActor =>
  def receive = LoggingReceive { eventSourceReceive }
}

object OSGIDummyListener {
  def apply()(implicit bundleContext : BundleContext) = new OSGIDummyListener() with OSGIEventSourceListener
}

class OSGIDummyListener extends Actor with ActorLogging with OSGIActor { this : OSGIEventSourceListener =>

  import akka.pattern._

  implicit val actorSys = context.system
  val latch = TestLatch(1)

  def initializing : Receive = {
    case InitializeBundle(_) => getActorConfig("listener") pipeTo(self)
    case ConfigLocatorResponse(bundleId, config) => {
      setupListener(config.getString("publisher"))
      context.system.eventStream.publish(BundleActorInitialized("listener"))
    }
    case "Andreas" => latch.countDown()
  }

  def receive = LoggingReceive { eventListenerReceive("publisher") orElse initializing }
}

class OSGIEventSourceListenerSpec extends WordSpec with Matchers {

  "The OSGI Event source listener" should {

    "subscribe to a publisher bundle if it already exists in the actor system" in new TestActorSys with TestSetup with MockitoSugar {

      import scala.concurrent.duration._

      val facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)
      val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
      val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

      listener ! InitializeBundle(osgiContext)

      // We need to wait for the Actor bundle to finish it's initialization
      system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
      fishForMessage() {
        case BundleActorInitialized(s) if s == "listener" => true
        case _ => false
      }

      publisher ! SendEvent("Andreas")

      val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
      Await.ready(latch, 1.second)
      latch.isOpen should be (true)
    }
  }

  "start referring to the dlc when the publisher is unavailbale" in new TestActorSys with TestSetup with MockitoSugar {

    val facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)
    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(system.deadLetters)
  }

  "subscribe to the publisher when it becomes available" in new TestActorSys with TestSetup with MockitoSugar {

    import scala.concurrent.duration._

    val facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)
    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    publisher ! SendEvent("Andreas")
    val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
    Await.ready(latch, 1.second)
    latch.isOpen should be (true)
  }

  "fallback to system.dlc when the publisher becomes unavailable" in new TestActorSys with TestSetup with MockitoSugar {

    val facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)
    val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

    listener ! InitializeBundle(osgiContext)

    // We need to wait for the Actor bundle to finish it's initialization
    system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
    fishForMessage() {
      case BundleActorInitialized(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    publisher ! PoisonPill

    listenerReal.publisher should be (system.deadLetters)
  }
}
