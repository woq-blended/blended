package blended.jms.utils

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import blended.streams.jms.internal.KeepAliveProducerFactory
import blended.streams.message.FlowEnvelope
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class JmsKeepAliveActorSpec extends TestKit(ActorSystem("JmsKeepAlive"))
  with LoggingFreeSpecLike
  with Matchers {

  private implicit val eCxtx : ExecutionContext = system.dispatcher

  class DummyKeepAliveProducer extends KeepAliveProducerFactory {

    val keepAliveEvents : mutable.ListBuffer[FlowEnvelope] = mutable.ListBuffer.empty

    override val createProducer: BlendedSingleConnectionFactory => Future[ActorRef] = { bcf => Future {
      system.actorOf(Props(new Actor() {
        override def receive: Receive = {
          case env : FlowEnvelope => keepAliveEvents.append(env)
          case m => println(m)
        }
      }))
    }}
  }

  "The JmsKeepAliveActor should" - {

    "issue a reconnect command when the number of missed keep alive messages exceeds the threshold" in pending

    "Initiate a keep alive message when the timeout has been reached" in pending

  }
}
