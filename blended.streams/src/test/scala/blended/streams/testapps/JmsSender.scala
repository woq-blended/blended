package blended.streams.testapps

import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import blended.streams.jms.JmsSinkStage
import blended.streams.message.{FlowMessage, MsgProperty}

object JmsSender extends AbstractStreamRunner("JmsSender") {

  def main(args: Array[String]) : Unit = {
    val header : Map[String, MsgProperty] = Map("foo" -> "bar", "small" -> 42)
    val msgs : List[FlowMessage] = List(
      FlowMessage(header, "Hallo Andreas"),
      FlowMessage(header, ByteString("Hello Streams!"))
    )


    val source = Source.fromIterator( () => msgs.toIterator )
    source.runWith(new JmsSinkStage(cf))

    //system.terminate()

  }
}
