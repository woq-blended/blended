package blended.streams.message

abstract class FlowEnvelope(msg: FlowMessage) {

  def acknowledge() : Unit

}
