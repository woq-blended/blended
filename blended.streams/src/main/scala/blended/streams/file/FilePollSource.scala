package blended.streams.file

import akka.actor.ActorSystem
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import blended.streams.AckSourceLogic
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope}
import blended.util.logging.Logger

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class FileAckSource(
  pollCfg : FilePollConfig
)(implicit system : ActorSystem) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val pollId : String =  s"${pollCfg.headerCfg.prefix}.FilePoller.${pollCfg.id}.source"
  private val out : Outlet[FlowEnvelope] = Outlet(name = pollId)

  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  private class FileSourceLogic() extends AckSourceLogic(out, shape) {
    /** The id to identify the instance in the log files */
    override def id: String = pollId

    /** A logger that must be defined by concrete implementations */
    override protected def log: Logger = Logger(pollId)

    /** The id's of the available inflight slots */
    override protected def inflightSlots(): List[String] =
      1.to(pollCfg.batchSize).map(i => s"FilePoller-${pollCfg.id}-$i").toList

    // Reset the polling interval
    override protected def nextPoll(): FiniteDuration = pollCfg.interval

    override protected def doPerformPoll(id: String, ackHandler: AcknowledgeHandler): Try[Option[Nothing]] = Try {
      None
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new FileSourceLogic()
}
