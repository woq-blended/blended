package blended.file

import scala.concurrent.duration.FiniteDuration

trait FileEnvelopeHeader {
  val fileHeaderPrefix : String => String = s => s + "File"

  val dirHeader : String => String = s => fileHeaderPrefix(s) + "Directory"
  val fileHeader : String => String = s => fileHeaderPrefix(s) + "File"
  val compressHeader : String => String = s => fileHeaderPrefix(s) + "Compressed"
  val appendHeader : String => String = s => fileHeaderPrefix(s) + "Append"
  val charsetHeader : String => String = s => fileHeaderPrefix(s) + "CharSet"
}

case class FileDropConfig(
  dirHeader : String,
  fileHeader : String,
  compressHeader : String,
  appendHeader : String,
  charsetHeader : String,
  defaultDir : String,
  dropTimeout : FiniteDuration
)
