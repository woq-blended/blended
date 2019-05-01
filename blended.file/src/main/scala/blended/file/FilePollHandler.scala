package blended.file

import java.io.File

import akka.actor.ActorSystem

import scala.concurrent.Future

trait FilePollHandler {

  def processFile(cmd : FileProcessCmd, f : File)(implicit system: ActorSystem) : Future[(FileProcessCmd, Option[Throwable])]
}
