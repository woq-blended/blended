package blended.streams

import blended.streams.message.FlowEnvelope

import scala.util.Try

case class FlowProcessor(

  f: FlowEnvelope => Try[FlowEnvelope]
)
