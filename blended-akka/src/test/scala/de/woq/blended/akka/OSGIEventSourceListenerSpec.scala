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

package de.woq.blended.akka

import akka.actor._
import akka.event.LoggingReceive
import akka.testkit.{TestActorRef, TestLatch}
import com.typesafe.config.Config
import de.woq.blended.testsupport.TestActorSys
import de.woq.blended.akka.internal.OSGIFacade
import de.woq.blended.akka.protocol._
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import akka.pattern.pipe

import scala.concurrent.Await

object OSGIActorDummyPublisher {
  def apply()(implicit bundleContext: BundleContext) = new OSGIActorDummyPublisher() with OSGIActor
}

class OSGIActorDummyPublisher extends OSGIActor with ProductionEventSource with BundleName {

  override def bundleSymbolicName = "publisher"

  def receive = LoggingReceive { eventSourceReceive }
}

object OSGIDummyListener {
  def apply()(implicit bundleContext : BundleContext) = new OSGIDummyListener() with OSGIEventSourceListener
}

class OSGIDummyListener extends InitializingActor with BundleName { this : OSGIEventSourceListener =>

  var publisherName : Option[String] = None

  implicit val actorSys = context.system
  val latch = TestLatch(1)

  def receive = initializing

  override def bundleSymbolicName = "listener"

  override def initialize(config: Config)(implicit bundleContext: BundleContext) : Unit = {

    publisherName = Some(config.getString("publisher"))
    setupListener(publisherName.get)
    context.system.eventStream.publish(BundleActorInitialized(bundleSymbolicName))
    self ! Initialized
  }

  def working = testing orElse(eventListenerReceive(publisherName.get))

  def testing : Receive = {
    case "Andreas" => latch.countDown()
  }
}

class OSGIEventSourceListenerSpec extends WordSpec with Matchers {

  "The OSGI Event source listener" should {

    "subscribe to a publisher bundle if it already exists in the actor system" in new TestActorSys with TestSetup with MockitoSugar {

      import scala.concurrent.duration._

      val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)
      val publisher = TestActorRef(Props(OSGIActorDummyPublisher()), "publisher")
      val listener = TestActorRef(Props(OSGIDummyListener()), "listener")

      listener ! InitializeBundle(osgiContext)

      // We need to wait for the Actor bundle to finish it's initialization
      system.eventStream.subscribe(testActor, classOf[BundleActorInitialized])
      fishForMessage() {
        case BundleActorInitialized(s) if s == "listener" => true
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

    val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)
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

    val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)
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

    val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)
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
