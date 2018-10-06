package blended.streams.testapps

import akka.stream.{KillSwitch, KillSwitches}
import akka.stream.scaladsl.{Keep, Sink, Source}
import blended.jms.utils.JmsQueue
import blended.streams.jms.{JMSConsumerSettings, JmsSinkStage, JmsSourceStage}
import blended.streams.message.{FlowMessage, MsgProperty}
import akka.pattern.after
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object JmsSender extends AbstractStreamRunner("JmsSender") {

  private[this] val log = LoggerFactory.getLogger("JmsSender")

  def main(args: Array[String]) : Unit = {

    val jmsBroker = broker()

    val header : Map[String, MsgProperty[_]] = Map("foo" -> "bar", "small" -> 42)

    val msgs = 1.to(10).map( i => FlowMessage(header, s"Message $i"))

    val source = Source.fromIterator( () => msgs.toIterator )
    source.runWith(new JmsSinkStage(cf))

    val cSettings = JMSConsumerSettings.create(cf).withDestination(JmsQueue("blended.test"))

    log.info("Created JMS consumer settings.")

    val (killSwitch, jmsSource)  = Source.fromGraph(new JmsSourceStage(cSettings))
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()

    after[Unit](1000.millis, system.scheduler)(Future(killSwitch.shutdown()))

    jmsSource.onComplete {
      case Success(l) =>
        log.info(s"Jms Source produced [${l.size}] messages : [${l.mkString(",")}]")
        jmsBroker.stop()
        jmsBroker.waitUntilStopped()
        system.terminate()

      case Failure(exception) =>
        exception.printStackTrace()
    }
  }
}
