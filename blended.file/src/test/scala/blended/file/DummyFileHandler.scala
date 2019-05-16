package blended.file
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

class FailingFileHandler extends FilePollHandler {

  private implicit val eCtxt : ExecutionContext = ExecutionContext.global

  override def processFile(cmd: FileProcessCmd) : Future[FileProcessResult] = {
    Future(FileProcessResult(cmd, Some(new Exception("Boom"))))
  }
}

class SucceedingFileHandler extends FilePollHandler {

  private implicit val eCtxt : ExecutionContext = ExecutionContext.global

  private val log : Logger = Logger[SucceedingFileHandler]
  var handled : List[FileProcessCmd] = List.empty

  override def processFile(cmd: FileProcessCmd): Future[FileProcessResult] = {
    log.info(s"Handling [$cmd]")
    handled = cmd :: handled
    Future(FileProcessResult(cmd, None))
  }
}
