package blended.file

import java.io.File

import akka.actor.ActorSystem

import scala.util.Try

trait FilePollHandler {

  def processFile(cmd : FileProcessCmd, f : File)(implicit system: ActorSystem) : Try[Unit]
}
