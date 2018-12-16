package blended.streams.processor

import akka.NotUsed
import akka.stream.scaladsl.Flow
import blended.streams.message.FlowEnvelope

class AckProcessor(val name : String) {

  override def toString() : String = {
    s"${getClass().getSimpleName()}($name)"
  }

  val flow : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
    if (env.requiresAcknowledge) {
      if (env.exception.isEmpty) {
        env.acknowledge()
      } else {
        env.deny()
      }
    }
    env
  }
}
