package blended.file
import java.io.InputStream

class TestFileHandler extends FilePollHandler {

  @throws
  override def processFile(is: InputStream, props: Map[String, Object]): Unit = {}
}
