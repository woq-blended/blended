package blended.file

import java.io.InputStream

trait FilePollHandler {

  @throws[Throwable]
  def processFile(is: InputStream, props: Map[String, Object]) : Unit
}
