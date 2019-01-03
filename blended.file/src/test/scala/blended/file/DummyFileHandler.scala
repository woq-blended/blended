package blended.file
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem

import scala.util.{Failure, Try}

class FailingFileHandler extends FilePollHandler {

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Try[Unit] =
    Failure(new Exception("Could not process !!"))
}

class SucceedingFileHandler extends FilePollHandler {

  val count : AtomicInteger = new AtomicInteger(0)

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Try[Unit] = Try {
    count.incrementAndGet()
  }
}
