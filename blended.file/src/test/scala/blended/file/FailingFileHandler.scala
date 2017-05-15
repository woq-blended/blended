package blended.file
import java.io.File

class FailingFileHandler extends FilePollHandler {

  @throws[Throwable]
  override def processFile(f : File): Unit =
    throw new Exception("Could not process !!")
}
