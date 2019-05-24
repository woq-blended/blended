package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import javax.jms._
import org.scalatest.FreeSpecLike

import scala.concurrent.duration._
import scala.util.Try

class ConnectionCloseActorSpec extends TestKit(ActorSystem("CloseActorSpec"))
  with FreeSpecLike
  with ImplicitSender {

  "The ConnectionCloseActor should" - {

    "answer with a ConnectionClosed message upon a successful close" in {
      val holder = new DummyHolder(() => new DummyConnection())
      holder.connect()

      val actor = system.actorOf(ConnectionCloseActor.props(holder))
      actor ! Disconnect(1.second)

      expectMsg(ConnectionClosed)
    }

    "answer with a CloseTimeout if the close does not succeed with in the specified timeout" in {

      val timeout = 50.millis

      val holder = new DummyHolder(() => new DummyConnection() {
        override def close(): Unit = {
          Thread.sleep((timeout * 2).toMillis)
          super.close()
        }
      })
      holder.connect()

      val actor = system.actorOf(ConnectionCloseActor.props(holder))
      actor ! Disconnect(timeout)

      expectMsg(CloseTimeout)
    }

    "do not retry the close if the call to close() threw an Exception" in {
      var closeCount : Int = 0

      val timeout = 50.millis

      val holder = new DummyHolder(() => new DummyConnection()) {
        override def close(): Try[Unit] = Try {
          closeCount += 1
          throw new JMSException("boom")
        }
      }

      val actor = system.actorOf(ConnectionCloseActor.props(holder, timeout))
      actor ! Disconnect(timeout * 4)

      expectMsg(ConnectionClosed)
      assert(closeCount == 1)
    }
  }

}
