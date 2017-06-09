package blended.file

import java.io.File

trait FilePollHandler {

  @throws[Throwable]
  def processFile(f : File) : Unit
}
