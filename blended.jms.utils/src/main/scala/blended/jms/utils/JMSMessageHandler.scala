package blended.jms.utils

import javax.jms.Message

@Deprecated
trait JMSMessageHandler {

  def handleMessage(msg : Message) : Option[Throwable]
}
