package blended.file

import javax.jms.ConnectionFactory
import akka.actor.ActorSystem

case class JMSFileDropConfig(
  system: ActorSystem,
  cf : ConnectionFactory,
  dest: String,
  errDest: String,
  dirHeader: String,
  fileHeader: String,
  compressHeader: String,
  appendHeader: String,
  charsetHeader: String,
  defaultDir : String
)
