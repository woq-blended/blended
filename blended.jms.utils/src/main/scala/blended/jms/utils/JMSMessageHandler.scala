package blended.jms.utils

import javax.jms.Message

trait JMSMessageHandler {

  def handleMessage(msg: Message) : Option[Throwable]
}
