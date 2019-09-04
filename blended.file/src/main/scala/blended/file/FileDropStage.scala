package blended.file

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import blended.streams.message.FlowEnvelope
import blended.streams.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * The Filedrop Stream consumes messages from a given upstream producing
  * FlowEnvelope. Each FlowEnvelope will by written to the designated file
  * drop location using an instance of a file drop actor. The file actor
  * responds with a FileDropResult, which is passed further downstream.
  *
  * Users of the Filedrop Stage must implement the logic of handling FileDropResults
  * if required.
  */
class FileDropStage(
  name : String,
  config: FileDropConfig,
  headerCfg : FlowHeaderConfig,
  dropActor: ActorRef,
  log: Logger
)(implicit system: ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FileDropResult]] {

  private val in = Inlet[FlowEnvelope](s"FileDropStream($name.in)")
  private val out = Outlet[FileDropResult](s"FileDropStream($name.out)")

  override def shape: FlowShape[FlowEnvelope, FileDropResult] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      private var results : List[FileDropResult] = List.empty
      private implicit val eCtxt : ExecutionContext = system.dispatcher

      private val dropper : EnvelopeFileDropper = new EnvelopeFileDropper(
        cfg = config, headerConfig = headerCfg, dropActor = dropActor, log = log
      )

      private val resultCallback : AsyncCallback[FileDropResult] = getAsyncCallback[FileDropResult]{ r =>
        log.debug(s"Filedrop result is [$r]")
        results = r :: results

        pushResult()

        // We are ready to receive a new File
        pull(in)
      }

      private def pushResult() : Unit = {
        if (results.nonEmpty && isAvailable(out)) {
          push(out, results.last)
          results = results.take(results.size - 1)
        }
      }

      // We kick off by signalling that we are ready to drop our first file.
      override def preStart(): Unit = pull(in)

      // The InHandler accepts messages from upstream and needs to kick off dropping the
      // message to the file system.
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val env = grab(in)
          log.debug(s"Filedropstage [$name] is processing envelope [${env.id}]")
          val (cmd, result) = dropper.dropEnvelope(env)

          result.onComplete {
            case Success(r) => resultCallback.invoke(r)
            case Failure(t) => resultCallback.invoke(FileDropResult(cmd, Some(t)))
          }
        }
      })

      // The outhandler grabs the Filedropresult if available and pushes it down stream
      setHandler(out, new OutHandler {
        override def onPull(): Unit = pushResult()
      })
    }
  }
}
