package blended.itestsupport

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.camel.CamelMessage
import akka.testkit.TestProbe
import akka.util.Timeout
import blended.testsupport.camel.{CamelMockActor, CamelTestSupport, MockAssertions}
import blended.testsupport.camel.protocol._
import org.apache.camel.CamelContext
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait ContainerSpecSupport { this: CamelTestSupport =>

  private[this] val log = LoggerFactory.getLogger(classOf[ContainerSpecSupport])

  def testMessage() : CamelMessage

  def outcome() : Map[String, Seq[MockAssertion]]

  // The standard black box test is to send a message to a given endpoint and check
  // the desired outcomes.
  def test(entry: String, testCooldown : FiniteDuration = 5.seconds)(implicit
    system: ActorSystem,
    camelContext: CamelContext,
    timeout: Timeout
  ) : List[Throwable] = {

    val mockUris = outcome()

    val mockProbe = new TestProbe(system)
    system.eventStream.subscribe(mockProbe.ref, classOf[MockActorReady])
    system.eventStream.subscribe(mockProbe.ref, classOf[MockMessageReceived])
    system.eventStream.subscribe(mockProbe.ref, classOf[ReceiveStopped])

    val mockActors = mockUris.keys.map{ uri =>
      uri -> system.actorOf(Props(CamelMockActor(uri)))
    }.toMap

    // We need to wait until all MockActors have been initialized
    mockProbe.receiveN(mockUris.size)

    val totalExpected = mockUris.values.flatten.foldLeft(0){ (sum, a) =>
      sum + (a match {
        case ExpectedMessageCount(c) => c
        case _ => 0
       })
    }

    log.info(s"The total number of expected out messages is [$totalExpected]")

    try {
      log.info(">" * 80)
      sendTestMessage(testMessage(), entry).get
      mockProbe.receiveN(totalExpected)
      // This will result in the entire List of assertion failures
      mockUris.map{ case (uri, assertions) => MockAssertions.checkAssertions(mockActors(uri), assertions:_*) }.flatten.toList
    } catch {
      case NonFatal(t) => List(t)
    } finally {
      log.info("-" * 80)
      log.info("Cleaning up after test")
      log.info("-" * 80)
      // We don't expect to receive any more messages within this test
      mockProbe.expectNoMsg(testCooldown)

      // Then we stop all mocks
      mockActors.values.foreach{ m => m.tell(StopReceive, mockProbe.ref) }
      mockProbe.receiveN(mockActors.size, timeout.duration)
      mockActors.values.foreach(m => m ! PoisonPill)

      system.eventStream.unsubscribe(mockProbe.ref)
      system.stop(mockProbe.ref)
      log.info("<" * 80)
    }
  }

}
