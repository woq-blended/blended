package blended.file
import java.io.InputStream

class TestFileHandler extends FilePollHandler {
  override def processFile(is: InputStream, props: Map[String, Object]): Option[Throwable] = None
}
