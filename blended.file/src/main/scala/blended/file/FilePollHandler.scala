package blended.file

import scala.concurrent.Future

trait FilePollHandler {

  def processFile(cmd : FileProcessCmd) : Future[FileProcessResult]
}
