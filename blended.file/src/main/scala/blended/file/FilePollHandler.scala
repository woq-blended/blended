package blended.file

import java.io.InputStream

trait FilePollHandler {

  def processFile(is: InputStream, props: Map[String, Object]) : Option[Throwable]
}
