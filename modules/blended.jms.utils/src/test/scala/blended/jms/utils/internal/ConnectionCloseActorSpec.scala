package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import blended.jms.utils.{BlendedJMSConnectionConfig, CloseTimeout, ConnectionClosed, Disconnect}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms._

import scala.concurrent.duration._
import scala.util.Try

class ConnectionCloseActorSpec extends TestKit(ActorSystem("CloseActorSpec"))
  with LoggingFreeSpecLike
  with ImplicitSender {

  "The ConnectionCloseActor should" - {

    "answer with a ConnectionClosed message upon a successful close" in {
      val holder = new DummyHolder(BlendedJMSConnectionConfig.defaultConfig.copy(
        vendor = "close", provider = "closed"
      ))
      holder.connect()

      val actor = system.actorOf(ConnectionCloseActor.props(holder))
      actor ! Disconnect(1.second)

      expectMsg(ConnectionClosed)
    }

    "answer with a CloseTimeout if the close does not succeed with in the specified timeout" in {

      val timeout = 100.millis

      val holder = new DummyHolder(BlendedJMSConnectionConfig.defaultConfig.copy(
        vendor = "close", provider = "closeTimeout"
      ), c => new DummyConnection(c)) {
        override def close() : Try[Unit] = {
          Thread.sleep((timeout * 2).toMillis)
          super.close()
        }
      }
      holder.connect()

      val actor = system.actorOf(ConnectionCloseActor.props(holder))
      actor ! Disconnect(timeout)

      expectMsg(CloseTimeout)
    }

    "do not retry the close if the call to close() threw an Exception" in {
      var closeCount : Int = 0

      val timeout = 50.millis

      val holder = new DummyHolder(BlendedJMSConnectionConfig.defaultConfig.copy(
        vendor = "close", provider = "closeOnce"
      ), c => new DummyConnection(c) {
        override def close(): Unit = {
          closeCount += 1
          throw new JMSException("boom")
        }
      })
      holder.connect()

      val actor = system.actorOf(ConnectionCloseActor.props(holder, timeout))
      actor ! Disconnect(timeout * 4)

      expectMsg(ConnectionClosed)
      assert(closeCount == 1)
    }
  }
}
