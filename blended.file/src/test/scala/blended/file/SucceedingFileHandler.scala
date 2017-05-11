package blended.file
import java.io.File

class SucceedingFileHandler extends FilePollHandler {

  @throws
  override def processFile(f: File, props: Map[String, Object]): Unit = {}
}
