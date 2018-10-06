package blended.streams.testapps

import akka.stream.scaladsl.{Keep, Sink, Source}
import blended.jms.utils.JmsQueue
import blended.streams.jms.{JMSConsumerSettings, JmsAckSourceStage, JmsSinkStage, JmsSourceStage}
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import akka.pattern.after
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object JmsSender extends AbstractStreamRunner("JmsSender") {

  def consume(withAck: Boolean) : Future[Seq[FlowEnvelope]] = {

    val cSettings = JMSConsumerSettings.create(cf).withDestination(JmsQueue("blended.test"))

    val (killSwitch, jmsSource) = if (withAck) {
      Source.fromGraph(new JmsAckSourceStage(cSettings))
        .toMat(Sink.seq)(Keep.both)
        .run()
    } else {
      Source.fromGraph(new JmsSourceStage(cSettings))
        .toMat(Sink.seq)(Keep.both)
        .run()
    }

    after[Unit](1000.millis, system.scheduler)(Future(killSwitch.shutdown()))

    jmsSource
  }

  private[this] val log = LoggerFactory.getLogger("JmsSender")

  def main(args: Array[String]) : Unit = {

    val jmsBroker = broker()

    val header : Map[String, MsgProperty[_]] = Map("foo" -> "bar", "small" -> 42)

    val msgs = 1.to(10).map( i => FlowMessage(s"Message $i", header))

    val source = Source.fromIterator( () => msgs.toIterator )
    source.runWith(new JmsSinkStage(cf))

    consume(true).onComplete {
      case Success(l) =>
        log.info(s"Jms Source produced [${l.size}] messages : [${l.mkString(",")}]")

      case Failure(exception) =>
        exception.printStackTrace()
    }

    Thread.sleep(3000)

    consume(false).onComplete {
      case Success(l) =>
        log.info(s"Jms Source produced [${l.size}] messages : [${l.mkString(",")}]")

      case Failure(exception) =>
        exception.printStackTrace()
    }

    Thread.sleep(3000)
    system.terminate()
  }
}
