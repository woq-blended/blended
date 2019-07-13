package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

class JmsKeepAliveActorSpec extends TestKit(ActorSystem("JmsKeepAlive"))
  with LoggingFreeSpecLike
  with Matchers {

  "The JmsKeepAliveActor should" - {
    "issue a reconnect command when the number of missed keep alive messages exceeds the threshold" in pending
  }
}
