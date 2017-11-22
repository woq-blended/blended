package blended.testsupport.camel

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import blended.testsupport.camel.protocol._

import scala.concurrent.Await

object MockAssertions {

  def checkAssertions(mock: ActorRef, assertions: MockAssertion*)(implicit timeout: Timeout) : List[Throwable] = {
    val f = (mock ? CheckAssertions(assertions)).mapTo[CheckResults]
    Await.result(f, timeout.duration).results.filter(_.isFailure).map(_.failed.get)
  }
}

class MockAssertions