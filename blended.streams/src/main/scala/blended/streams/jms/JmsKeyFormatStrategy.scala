package blended.streams.jms

trait JmsKeyFormatStrategy {
  def handleReceivedKey(key : String) : String
  def handleSendKey(key : String) : String
}

object DefaultKeyFormatStrategy {
  val hyphen : String = "-"
  val dot : String = "."
  val hyphen_repl : String = "_HYPHEN_"
  val dot_repl : String = "_DOT_"
}

class DefaultKeyFormatStrategy extends JmsKeyFormatStrategy {

 import DefaultKeyFormatStrategy._

  override def handleReceivedKey(key: String): String = key
    .replaceAll(hyphen_repl, hyphen)
    .replaceAll(dot_repl, dot)

  override def handleSendKey(key: String): String = key
    .replaceAll(hyphen, hyphen_repl)
    .replaceAll("\\" + dot, dot_repl)
}

class PassThroughKeyFormatStrategy extends JmsKeyFormatStrategy {

  override def handleReceivedKey(key: String): String = key
  override def handleSendKey(key: String): String = key
}
