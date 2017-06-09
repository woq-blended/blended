package blended.file

import javax.jms.Message

trait JMSFileDropErrorHandler {

  def handleError(msg: Message, cfg: JMSFileDropConfig) : Unit
}
