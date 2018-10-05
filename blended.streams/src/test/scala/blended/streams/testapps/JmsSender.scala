package blended.streams.testapps

import akka.stream.KillSwitch
import akka.stream.scaladsl.{Sink, Source}
import blended.jms.utils.JmsQueue
import blended.streams.jms.{JMSConsumerSettings, JmsSinkStage, JmsSourceStage}
import blended.streams.message.{FlowMessage, MsgProperty}

import scala.concurrent.duration._

object JmsSender extends AbstractStreamRunner("JmsSender") {

  def main(args: Array[String]) : Unit = {

    val jmsBroker = broker()

    val header : Map[String, MsgProperty[_]] = Map("foo" -> "bar", "small" -> 42)

    val msgs = 1.to(10).map( i => FlowMessage(header, s"Message $i"))

    val source = Source.fromIterator( () => msgs.toIterator )
    source.runWith(new JmsSinkStage(cf))

    val cSettings = JMSConsumerSettings(
      cf,
      1.second,
      JmsQueue("blended.test")
    )

    Thread.sleep(10000)

    val jmsSource : Source[FlowMessage, KillSwitch] = Source.fromGraph(new JmsSourceStage(cSettings))
    val f = jmsSource.take(5).runWith(Sink.seq)
    f.onComplete( msgs => msgs.get.foreach(println))

    Thread.sleep(10000)

    system.terminate().onComplete { _ =>
      jmsBroker.stop()
      jmsBroker.waitUntilStopped()
    }
  }
}
