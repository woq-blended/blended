package blended.streams.processor

import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.util.logging.Logger

import scala.util.{Failure, Success}

case class AckProcessor(name : String, log: Logger) extends FlowProcessor {

  override val f: IntegrationStep = { env =>
    env.exception match {
      case Some(t) =>
        Failure(t)
      case None =>
        env.acknowledge()
        Success(List(env))
    }
  }
}
