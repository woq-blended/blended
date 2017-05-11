package blended.file
import java.io.InputStream

class FailingFileHandler extends FilePollHandler {

  @throws[Throwable]
  override def processFile(is: InputStream, props: Map[String, Object]): Unit =
    throw new Exception("Could not process !!")
}
