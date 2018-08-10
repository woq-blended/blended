package blended.itestsupport

import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.camel.CamelMessage
import akka.testkit.TestProbe
import akka.util.Timeout
import blended.testsupport.camel._
import blended.testsupport.camel.protocol._
import blended.util.logging.Logger
import org.apache.camel.CamelContext

trait ContainerSpecSupport { this: CamelTestSupport =>

  private[this] val log = Logger[ContainerSpecSupport]

  def blackboxTest(
    message: CamelMessage,
    entry: String,
    outcome: Map[String, Seq[MockAssertion]],
    testCooldown: FiniteDuration
  )(implicit
    system: ActorSystem,
    camelContext: CamelContext,
    timeout: Timeout
  ): List[Throwable] = blackboxTest(
    input = Map(entry -> message),
    outcome = outcome,
    testCooldown = testCooldown
  )

  // The standard black box test is to send an arbitrary number of  messages to
  // the container and inspect the desired outcomes.
  def blackboxTest(
    input: Map[String, CamelMessage],
    outcome: Map[String, Seq[MockAssertion]],
    testCooldown: FiniteDuration
  )(implicit
    system: ActorSystem,
    camelContext: CamelContext,
    timeout: Timeout
  ): List[Throwable] = {

    val readyProbe = TestProbe()
    val receiveProbe = TestProbe()
    val stopProbe = TestProbe()

    system.eventStream.subscribe(readyProbe.ref, classOf[MockActorReady])
    system.eventStream.subscribe(receiveProbe.ref, classOf[MockMessageReceived])
    system.eventStream.subscribe(stopProbe.ref, classOf[ReceiveStopped])

    val mockActors = outcome.keys.map { uri =>
      uri -> system.actorOf(Props(CamelMockActor(uri)))
    }.toMap

    // We need to wait until all MockActors have been initialized
    readyProbe.receiveN(mockActors.size, testCooldown)

    val totalExpected = outcome.values.flatten.foldLeft(0) { (sum, a) =>
      sum + (a match {
        case ExpectedMessageCount(c) => c
        case MinMessageCount(c) => c
        case _ => 0
      })
    }

    log.info(s"The total number of expected out messages is [$totalExpected]")

    try {
      log.info(">" * 80)
      input.foreach { case (entry, message) => sendTestMessage(message, entry).get }
      receiveProbe.receiveN(totalExpected, timeout.duration)
      // This will result in the entire List of assertion failures
      val ctResults = outcome.flatMap { case (uri, assertions) => MockAssertion.checkAssertions(mockActors(uri), assertions: _*) }.toList

      val unexpected = {
        val msgs = receiveProbe.receiveWhile(testCooldown) {
          case m => m.asInstanceOf[MockMessageReceived]
        }

        log.debug(s"Raw unexpected messages : [${msgs.mkString(",")}]")

        val urisWithMinimum = outcome.filter {
          case (uri, asserts) =>
            asserts.find(_.isInstanceOf[MinMessageCount]).isDefined
        }.keys.toSeq

        msgs.filter { msg => !urisWithMinimum.contains(msg.uri) } match {
          case Nil => Nil
          case l => List(new Exception("Received unexpected messages " + l.map(_.msg).mkString("[", ",", "]")))
        }
      }

      ctResults ::: unexpected
    } catch {
      case NonFatal(t) => List(t)
    } finally {
      log.info("-" * 80)
      log.info("Cleaning up after test")
      log.info("-" * 80)

      // Then we stop all mocks
      mockActors.values.foreach { m => m.tell(StopReceive, stopProbe.ref) }
      stopProbe.receiveN(mockActors.size, timeout.duration)
      mockActors.values.foreach(m => m ! PoisonPill)

      system.stop(receiveProbe.ref)
      system.stop(stopProbe.ref)
      system.stop(readyProbe.ref)
      log.info("<" * 80)
    }
  }
}
