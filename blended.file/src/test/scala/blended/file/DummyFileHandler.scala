package blended.file
import java.io.File

import akka.actor.ActorSystem
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

class FailingFileHandler extends FilePollHandler {

  private implicit val eCtxt : ExecutionContext = ExecutionContext.global

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Future[(FileProcessCmd, Option[Throwable])] =
    Future((cmd, Some(new Exception("Boom"))))
}

class SucceedingFileHandler extends FilePollHandler {

  private implicit val eCtxt : ExecutionContext = ExecutionContext.global

  private val log : Logger = Logger[SucceedingFileHandler]
  var handled : List[FileProcessCmd] = List.empty

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Future[(FileProcessCmd, Option[Throwable])] = {
    log.info(s"Handling [$cmd]")
    Future(cmd, None)
  }
}
