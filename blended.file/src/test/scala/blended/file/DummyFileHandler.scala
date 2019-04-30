package blended.file
import java.io.File

import akka.actor.ActorSystem
import blended.util.logging.Logger

import scala.util.{Failure, Try}

class FailingFileHandler extends FilePollHandler {

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Try[Unit] =
    Failure(new Exception("Could not process !!"))
}

class SucceedingFileHandler extends FilePollHandler {

  private val log : Logger = Logger[SucceedingFileHandler]

  var handled : List[FileProcessCmd] = List.empty

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Try[Unit] = Try {
    log.info(s"Handling [$cmd]")
    handled = cmd :: handled
  }
}
