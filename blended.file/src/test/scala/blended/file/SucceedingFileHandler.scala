package blended.file
import java.io.File

import akka.actor.ActorSystem

import scala.util.Try

class SucceedingFileHandler extends FilePollHandler {

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Try[Unit] = Try {}
}
