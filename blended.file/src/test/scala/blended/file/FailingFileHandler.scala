package blended.file
import java.io.File

class FailingFileHandler extends FilePollHandler {

  @throws[Throwable]
  override def processFile(cmd: FileProcessCmd, f : File): Unit =
    throw new Exception("Could not process !!")
}
