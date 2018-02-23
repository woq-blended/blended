package blended.jms.utils

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import blended.jms.utils.internal._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpecLike, Matchers}

import scala.concurrent.duration._

class ConnectionPingActorSpec extends TestKit(ActorSystem("ConnectionPingSpec"))
  with FreeSpecLike
  with Matchers
  with MockitoSugar
  with ImplicitSender {

  class HealthyPerformer extends Actor {
    override def receive: Receive = {
      case ExecutePing(pingActor) => pingActor ! PingResult(Right("Hooray"))
    }
  }

  object ExceptionPerformer{
    def props(e: Exception) : Props = Props(new ExceptionPerformer(e))
  }

  class ExceptionPerformer(e: Exception) extends Actor {
    override def receive: Receive = {
      case ExecutePing(pingActor) => pingActor ! PingResult(Left(e))
    }
  }

  class IrresponsivePerformer extends Actor {
    override def receive: Receive = {
      case ExecutePing(pingActor) =>
    }
  }

  "The ConnectionPingActor" - {

    "should respond with a ConnectionHealthy message if the connection is fine" in {

      val testActor = system.actorOf(ConnectionPingActor.props(1.second))

      testActor ! Props(new HealthyPerformer())

      expectMsg(PingResult(Right("Hooray")))
    }

    "should respond with a Timeout message if the connection cannot be pinged" in {

      val testActor = system.actorOf(ConnectionPingActor.props(1.second))

      testActor ! Props(new IrresponsivePerformer())

      expectMsg(PingTimeout)
    }

    "should respond with a negative ping result message if the performer throws an exception" in {
      val e = new RuntimeException("boom")

      val testActor = system.actorOf(ConnectionPingActor.props(1.second))

      testActor ! ExceptionPerformer.props(e)

      expectMsg(PingResult(Left(e)))
    }
  }
}
