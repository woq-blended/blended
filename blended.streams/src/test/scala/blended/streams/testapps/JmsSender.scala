package blended.streams.testapps

import java.util.UUID

import akka.NotUsed
import akka.pattern.after
import akka.stream.scaladsl.{Flow, Keep, RestartFlow, RestartSource, Sink, Source}
import akka.stream.{KillSwitch, KillSwitches}
import blended.jms.utils.JmsQueue
import blended.streams.jms._
import blended.streams.message.{DefaultFlowEnvelope, FlowEnvelope, FlowMessage, MsgProperty}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object JmsSender extends AbstractStreamRunner("JmsSender") {

  def consume(withAck: Boolean) : Future[Seq[FlowEnvelope]] = {

    val cSettings = JMSConsumerSettings.create(cf).withDestination(JmsQueue("blended.test"))

    val innerSource : Source[FlowEnvelope, KillSwitch]= if (withAck) {
      Source.fromGraph(new JmsAckSourceStage(cSettings, system))
    } else {
      Source.fromGraph(new JmsSourceStage(cSettings, system))
    }

    val source : Source[FlowEnvelope, NotUsed] = RestartSource.onFailuresWithBackoff(
      minBackoff = 2.seconds,
      maxBackoff = 10.seconds,
      randomFactor = 0.2,
      maxRestarts = -1,
    ) { () => innerSource }

    val (killSwitch, jmsFlow) = source
      .viaMat(KillSwitches.single)(Keep.right)
     .filter { env =>
        env.flowMessage.header[Int]("msgno") match {
          case Some(p) => p != 5
          case None => false
        }
      }
      .map{ env =>
        env.acknowledge()
        env
      }
      .toMat(Sink.seq)(Keep.both)
      .run()

    after[Unit](3000.millis, system.scheduler)(Future(killSwitch.shutdown()))

    jmsFlow
  }

  private[this] val log = LoggerFactory.getLogger("JmsSender")

  def main(args: Array[String]) : Unit = {

    val jmsBroker = broker()

    val msgs = 1.to(10).map { i =>
      val header : Map[String, MsgProperty[_]] = Map("foo" -> "bar", "msgno" -> i)
      FlowMessage(s"Message $i", header)
    }

    val settings : JmsProducerSettings = JmsProducerSettings(
      connectionFactory = cf,
      connectionTimeout = 1.second,
      jmsDestination = Some(JmsQueue("blended.test")),
      correlationId = () => Some(UUID.randomUUID().toString())
    )

    val flow : Flow[FlowEnvelope, FlowEnvelope, _] = RestartFlow.onFailuresWithBackoff[FlowEnvelope, FlowEnvelope](
      minBackoff = 2.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2,
      maxRestarts = -1,
    ){ () => Flow.fromGraph(new JmsSinkStage(settings)) }

    val source = Source
      .fromIterator( () => msgs.toIterator)
      .map(m => DefaultFlowEnvelope(m))
      .via(flow)

    source.runWith(Sink.seq).onComplete{
      case Success(msgs) => log.info(msgs.mkString("\n"))
      case Failure(t) => t.printStackTrace()
    }


//
//    consume(true).onComplete {
//      case Success(l) =>
//        log.info(s"Jms Source produced [${l.size}] messages : [${l.map(_.flowMessage).mkString("\n")}]")
//
//      case Failure(exception) =>
//        exception.printStackTrace()
//    }
//
//    Thread.sleep(5000)
//
//    consume(false).onComplete {
//      case Success(l) =>
//        log.info(s"Jms Source produced [${l.size}] messages : [${l.map(_.flowMessage).mkString("\n")}]")
//
//      case Failure(exception) =>
//        exception.printStackTrace()
//    }
//
    Thread.sleep(3600000)
    jmsBroker.stop()
    system.terminate()
  }
}
