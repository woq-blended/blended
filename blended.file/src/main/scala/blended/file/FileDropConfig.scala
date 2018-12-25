package blended.file

import akka.actor.ActorSystem

case class FileDropConfig(
  system: ActorSystem,
  dirHeader: String,
  fileHeader: String,
  compressHeader: String,
  appendHeader: String,
  charsetHeader: String,
  defaultDir : String,
  dropTimeout : Int,
  dropNotification: Boolean
)
