package blended.jms.bridge

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import blended.streams.jms.{JmsProducerSettings, JmsSinkStage}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.util.logging.Logger

object JmsProducerSupport {

  def jmsProducer(
    name: String,
    settings: JmsProducerSettings,
    autoAck: Boolean = false,
    log: Option[Logger]
  )(implicit system: ActorSystem, materializer: Materializer): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val f = Flow.fromGraph(new JmsSinkStage(name, settings))

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow(log))
    } else {
      f
    }
  }
}
