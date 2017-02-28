package blended.jms.utils.internal

case object PingConnection
case object CheckConnection
case object ConnectionClosed
case object CloseTimeout

case object PingTimeout
case class PingResult(result : Either[Throwable, String])
case class PingReceived(s : String)
