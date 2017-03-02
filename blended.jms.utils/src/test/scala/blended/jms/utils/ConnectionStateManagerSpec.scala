package blended.jms.utils

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{FreeSpecLike, Matchers}

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with FreeSpecLike
  with Matchers
  with ImplicitSender {

  "The Connection State Manager" - {

    "should start in disconnected state" in {
      pending
    }

    "should switch to connected state upon successful connect" in {
      pending
    }

    "should switch to disconnected state upon successful disconnect" in {
      pending
    }

    "should disconnect after the specified number of failed pings" in {
      pending
    }

    
  }
}
