package blended.jms.bridge.internal

import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Source}
import blended.jms.utils.JmsQueue
import blended.streams.{StreamController, StreamControllerConfig}
import blended.streams.jms._
import blended.streams.message.{DefaultFlowEnvelope, FlowEnvelope, FlowMessage, MsgProperty}
import blended.util.logging.Logger

import scala.concurrent.duration._

object JmsSender extends AbstractStreamRunner("JmsSender") {

  private[this] val log = Logger[JmsSender.type]

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

    val sink = Flow[FlowMessage]
      .map(m => DefaultFlowEnvelope(m))
      .viaMat(Flow.fromGraph(new JmsSinkStage(settings)))(Keep.left)

    val foo : Source[FlowEnvelope, NotUsed] = Source
      .fromIterator( () => msgs.toIterator)
      .viaMat(sink)(Keep.right)

    val streamRunner = system.actorOf(StreamController.props(StreamControllerConfig(
      name = "jmsSender",
      stream = foo,
    )))

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
