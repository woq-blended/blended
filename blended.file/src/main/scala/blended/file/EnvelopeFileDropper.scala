package blended.file

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import blended.streams.jms.JmsEnvelopeHeader
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, TextFlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import blended.util.RichTry._

class EnvelopeFileDropper(
  cfg : FileDropConfig,
  headerConfig : FlowHeaderConfig,
  dropActor : ActorRef,
  log : Logger
)(implicit system : ActorSystem) extends JmsEnvelopeHeader {

  // Get the content of the envelope as a ByteString which we can write to disk
  private def extractContent(env : FlowEnvelope) : Try[ByteString] = Try {
    env.flowMessage match {
      case tMsg : TextFlowMessage =>
        val charSet = env.headerWithDefault[String](cfg.charsetHeader, "UTF-8")
        log.info(s"Using charset [$charSet] to file drop text message [${env.id}]")
        ByteString(tMsg.getText().getBytes(charSet))

      case bMsg : BinaryFlowMessage =>
        bMsg.content

      case m =>
        val eTxt = s"Dropping files unsupported for msg [${env.id}] of type [${m.getClass.getName}]"
        log.error(eTxt)
        throw new Exception(eTxt)
    }
  }

  // Try to get the correlation Id from the message, fall back with the correlation ID from
  // the FlowEnvelope, finally fall back with the envelope ID
  private[this] def corrId(env : FlowEnvelope) : String = {
    env.headerWithDefault[String](
      "JMSCorrelationID",
      env.headerWithDefault[String](corrIdHeader(headerConfig.prefix), env.id)
    )
  }

  // extract the drop Command from the envelope
  private[this] def dropCmd(env : FlowEnvelope)(f : FlowEnvelope => Try[ByteString]) : Try[FileDropCommand] = Try {
    FileDropCommand(
      id = env.id,
      content = f(env).unwrap,
      directory = env.headerWithDefault[String](cfg.dirHeader, cfg.defaultDir),
      fileName = env.headerWithDefault[String](cfg.fileHeader, ""),
      compressed = env.headerWithDefault[Boolean](cfg.compressHeader, false),
      append = env.headerWithDefault[Boolean](cfg.appendHeader, false),
      timestamp = env.headerWithDefault[Long](timestampHeader(headerConfig.prefix), System.currentTimeMillis()),
      properties = Map("JMSCorrelationID" -> corrId(env)) ++ env.flowMessage.header.mapValues(_.value)
    )
  }

  private[this] def handleError(env : FlowEnvelope, error : Throwable) : FileDropResult = {
    log.error(s"Error dropping envelope [${env.id}] to file : [${error.getMessage()}]")
    val cmd = dropCmd(env)(_ => Success(ByteString(""))).unwrap
    dropActor ! FileDropAbort(error)
    FileDropResult(cmd, Some(error))
  }

  def dropEnvelope(env : FlowEnvelope) : (FileDropCommand, Future[FileDropResult]) = {

    val p : Promise[FileDropResult] = Promise()

    dropCmd(env)(extractContent) match {
      case Success(cmd) =>
        implicit val to : Timeout = Timeout(cfg.dropTimeout)
        implicit val eCtxt : ExecutionContext = system.dispatcher

        (dropActor ? cmd).mapTo[FileDropResult].onComplete {
          case Success(r) => p.complete(Success(r))
          case Failure(t) => p.complete(Success(handleError(env, t)))
        }

        (cmd, p.future)

      case Failure(t) =>
        val r : FileDropResult = handleError(env, t)
        p.complete(Success(r))
        (r.cmd, p.future)
    }
  }
}
