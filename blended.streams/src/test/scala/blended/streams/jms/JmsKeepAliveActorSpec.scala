package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

import scala.concurrent.duration._

class JmsKeepAliveActorSpec extends TestKit(ActorSystem("JmsKeepAlive"))
  with LoggingFreeSpecLike
  with Matchers {

  private val kaInterval : FiniteDuration = 100.millis
  private val vendor = "sagum"
  private val provider = "central"

  "The JmsKeepAliveActor should" - {

    "issue a reconnect command when the number of missed keep alive messages exceeds the threshold" in pending

    "Initiate a keep alive message when the timeout has been reached" in pending

  }
}
