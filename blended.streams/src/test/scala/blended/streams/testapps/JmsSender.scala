package blended.streams.testapps

import akka.stream.scaladsl.Source
import blended.streams.jms.JmsSinkStage
import blended.streams.message.{FlowMessage, MsgProperty}

object JmsSender extends AbstractStreamRunner("JmsSender") {

  def main(args: Array[String]) : Unit = {

    val jmsBroker = broker()

    val header : Map[String, MsgProperty] = Map("foo" -> "bar", "small" -> 42)

    val msgs = (1.to(100)).map( i => FlowMessage(header, s"Message $i"))

    val source = Source.fromIterator( () => msgs.toIterator )
    source.runWith(new JmsSinkStage(cf))

    //system.terminate()
  }
}
