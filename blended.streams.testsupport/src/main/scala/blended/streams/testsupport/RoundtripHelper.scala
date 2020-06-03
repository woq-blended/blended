package blended.streams.testsupport

import akka.actor.ActorSystem
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.Collector
import blended.streams.FlowHeaderConfig
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
  headerConfig: FlowHeaderConfig = FlowHeaderConfig.create(prefix = "SIB"),
  outcome : Seq[ExpectedOutcome] = Seq.empty
)(implicit system : ActorSystem) extends JmsStreamSupport {

  val outcomeId : ExpectedOutcome => String = oc => oc.cf.id + "." + oc.dest.asString

  private implicit val eCtxt : ExecutionContext = system.dispatcher

  def withTestMsgs(env : FlowEnvelope*) : RoundtripHelper = copy(testMsgs = testMsgs ++ env)
  def withTimeout(to : FiniteDuration) : RoundtripHelper = copy(timeout = to)
  def withHeaderConfig(cfg : FlowHeaderConfig) : RoundtripHelper = copy(headerConfig = cfg)
  def withOutcome(o : ExpectedOutcome*) : RoundtripHelper = copy(outcome = outcome ++ o)

  val log : Logger = Logger(classOf[RoundtripHelper].getName() + "." + name)
  val envLog : FlowEnvelopeLogger = new FlowEnvelopeLogger(log, headerConfig.prefix)

  def run() : Map[String, Seq[String]] = {
    val msg : String = s"Starting test case [$name], timeout = [$timeout]"
    println(msg)
    log.info(msg)

    val collectors : Map[String, Collector[FlowEnvelope]] = {
      outcome.map { o =>
        val k : String  = outcomeId(o)
        val c = receiveMessages(
          headerCfg = headerConfig,
          cf = o.cf,
          dest = o.dest,
          log = envLog,
          timeout = Some(timeout)
        )
        k -> c
      }
    }.toMap

    val pSettings: JmsProducerSettings = JmsProducerSettings(
      log = envLog,
      headerCfg = headerConfig,
      connectionFactory = inbound._1,
      jmsDestination = Some(inbound._2)
    )
    // Send the inbound messages
    sendMessages(pSettings, envLog, testMsgs:_*)

    // Wait for all outcomes

    val mappedResults : Future[Map[String, List[FlowEnvelope]]] = {

      val seq : Seq[Future[(String, List[FlowEnvelope])]] = collectors.map {
        case (k, v) =>
          v.result.map { r => (k, r) }
      }.toSeq

      Future.sequence(seq).map(_.toMap)
    }

    val results : Map[String, List[FlowEnvelope]] = Await.result(mappedResults, timeout + 1.second)

    val msgs : Map[String, Seq[String]] = results.map {
      case (k, v) =>
        val oc : ExpectedOutcome = outcome.find(o => outcomeId(o).equals(k)).get
        k -> FlowMessageAssertion.checkAssertions(v : _*)(oc.assertion : _*)
    }

    log.info(s"Finishing test case [$name]")
    collectors.values.foreach(c => system.stop(c.actor))

    msgs.filter(_._2.nonEmpty)
  }
}
