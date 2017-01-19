package blended.jms.utils.internal

import javax.jms.Message

case object PingConnection
case object CheckConnection
case object ConnectionClosed
case object CloseTimeout

case object PingTimeout
case class PingResult(result : Either[Throwable, Message])


