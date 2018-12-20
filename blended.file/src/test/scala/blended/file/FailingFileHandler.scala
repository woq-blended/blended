package blended.file
import java.io.File

import akka.actor.ActorSystem

import scala.util.{Failure, Try}

class FailingFileHandler extends FilePollHandler {

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Try[Unit] =
    Failure(new Exception("Could not process !!"))
}
