package blended.file
import java.io.File

class FailingFileHandler extends FilePollHandler {

  @throws[Throwable]
  override def processFile(f : File, props: Map[String, Object]): Unit =
    throw new Exception("Could not process !!")
}
