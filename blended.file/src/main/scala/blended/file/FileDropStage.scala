package blended.file

import akka.actor.ActorSystem
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

/**
  * The Filedrop Stream consumes messages from a given upstream producing
  * FlowEnvelope. Each FlowEnvelope will by writen to the designated file
  * drop location using an instance of a file drop actor. The file actor
  * responds with a FileDropResult, which is passed further downstream.
  *
  * Users of the Filedrop Stage must implement the logic of handling FileDropResults
  * if required.
  *
  * @param name
  * @param config
  * @param log
  * @param system
  */
class FileDropStage(
  name : String, config: FileDropConfig, log: Logger
)(implicit system: ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FileDropResult]] {

  private val in = Inlet[FlowEnvelope](s"FileDropStream($name.in)")
  private val out = Outlet[FileDropResult](s"FileDropStream($name.out)")

  override def shape: FlowShape[FlowEnvelope, FileDropResult] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      // We kick off by signalling that we are ready to drop our first file.
      override def preStart(): Unit = pull(in)
    }
  }
}
