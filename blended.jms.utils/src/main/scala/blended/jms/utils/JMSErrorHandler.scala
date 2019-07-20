package blended.jms.utils

import javax.jms.{ConnectionFactory, Message}

@Deprecated
trait JMSErrorHandler {

  def handleError(m : Message, t : Throwable) : Boolean
}

@Deprecated
class RedeliveryErrorHandler extends JMSErrorHandler {
  override def handleError(msg : Message, t : Throwable) : Boolean = false
}

@Deprecated
class ForwardingErrorHandler(cf : ConnectionFactory, dest : String) extends JMSErrorHandler {

  private[this] val fwdHandler = new ForwardingMessageHandler(cf, dest)

  override def handleError(msg : Message, t : Throwable) : Boolean = {
    fwdHandler.handleMessage(msg).isEmpty
  }
}
