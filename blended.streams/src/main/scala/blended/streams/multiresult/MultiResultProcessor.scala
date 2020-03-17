package blended.streams.multiresult

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.Logger

import scala.concurrent.duration._

/**
 * Process the results of a MultiResultGraphStage and produce a single envelope as result.
 *
 * The MultiResultProcessor processes an incoming envelope with a given MultiResultGraphstage
 * with a given Flow[FlowEnvelope, FlowEnvelope]. It will collect the results from the sub flow
 * and
 *
 * Pass the original envelope downstream if all results have been processed successfully.
 * Pass the original envelope downstream with a contained exception if at least one of the results
 * has not been successful.
 */
class MultiResultProcessor(
  replicator : FlowEnvelope => List[FlowEnvelope],
  processSingle : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
  timeout : Option[FiniteDuration],
  log : FlowEnvelopeLogger
)(implicit system: ActorSystem) {

  private val processor : ActorRef = system.actorOf(MultiResultController.props(
    replicator, processSingle, timeout, log
  ))

  def build() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    Flow[FlowEnvelope]
      .mapAsync(1){
        case env : FlowEnvelope =>
          implicit val to : Timeout = Timeout(timeout.map(_ * 2).getOrElse(1.minute))
          (processor ? env).mapTo[FlowEnvelope]
      }

}
