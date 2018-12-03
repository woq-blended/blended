package blended.streams.testsupport

package sib.itest.streams

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class ExpectedOutcome(
  cf : IdAwareConnectionFactory,
  dest : JmsDestination,
  assertion : Seq[FlowMessageAssertion]
)

case class RoundtripHelper(
  name : String,
  inbound : (IdAwareConnectionFactory, JmsDestination),
  testMsgs : Seq[FlowEnvelope] = Seq.empty,
  timeout : FiniteDuration = 10.seconds,
  headerConfig: FlowHeaderConfig = FlowHeaderConfig(prefix = "SIB"),
  outcome : Seq[ExpectedOutcome] = Seq.empty
)(implicit system: ActorSystem) extends JmsStreamSupport {

  val outcomeId : ExpectedOutcome => String = oc => oc.cf.id + "." + oc.dest.asString

  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val to : FiniteDuration = timeout

  def withTestMsgs(env: FlowEnvelope*): RoundtripHelper = copy(testMsgs = testMsgs ++ env)
  def withTimeout(to : FiniteDuration): RoundtripHelper = copy(timeout = to)
  def withHeaderConfig(cfg: FlowHeaderConfig): RoundtripHelper = copy(headerConfig = cfg)
  def withOutcome(o : ExpectedOutcome*) : RoundtripHelper = copy(outcome = outcome ++ o)

  val log : Logger = Logger(classOf[RoundtripHelper].getName() + "." + name)

  def run() : Map[String, Seq[String]] = {
    log.info(s"Starting test case [$name]")

    val collectors : Map[String, Collector[FlowEnvelope]] = {
      outcome.map { o =>
        val k : String  = outcomeId(o)
        val c = receiveMessages(headerConfig, o.cf, o.dest, log)
        k -> c
      }
    }.toMap

    // Send the inbound messages
    sendMessages(inbound._1, inbound._2, log, testMsgs:_*)

    // Wait for all outcomes

    val mappedResults : Future[Map[String, List[FlowEnvelope]]] = {

      val seq : Seq[Future[(String, List[FlowEnvelope])]] = collectors.map { case (k,v) =>
        v.result.map { r => (k, r)}
      }.toSeq

      Future.sequence(seq).map(_.toMap)
    }

    val results : Map[String, List[FlowEnvelope]] = Await.result(mappedResults, timeout + 1.second)

    val msgs : Map[String, Seq[String]] = results.map { case (k,v) =>
      val oc : ExpectedOutcome = outcome.find(o => outcomeId(o).equals(k)).get
      k -> FlowMessageAssertion.checkAssertions(v:_*)(oc.assertion:_*)
    }

    log.info(s"Finishing test case [$name]")

    msgs.filter(_._2.nonEmpty)
  }
}
