package blended.file
import java.io.File

class SucceedingFileHandler extends FilePollHandler {

  @throws
  override def processFile(cmd: FileProcessCmd, f: File): Unit = {}
}
