package blended.jms.bridge

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{RestartSource, Source}
import blended.streams.jms.{JMSConsumerSettings, JmsAckSourceStage, JmsSourceStage}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._

object RestartableJmsSource {

  def apply(
    name : String,
    settings : JMSConsumerSettings,
    log : Logger = Logger[RestartableJmsSource.type],
    requiresAck : Boolean = true,
    minBackoff : FiniteDuration = 2.seconds,
    maxBackoff : FiniteDuration = 10.seconds,
    maxRestarts : Long = -1
  )(implicit system : ActorSystem) : Source[FlowEnvelope, NotUsed] = {

    val innerSource : Source[FlowEnvelope, NotUsed]= if (requiresAck) {
      Source.fromGraph(new JmsAckSourceStage(name, settings, log))
    } else {
      Source.fromGraph(new JmsSourceStage(name, settings, log))
    }

    RestartSource.onFailuresWithBackoff(
      minBackoff = 2.seconds,
      maxBackoff = 10.seconds,
      randomFactor = 0.2,
      maxRestarts = 10,
    ) { () => innerSource }
  }
}

