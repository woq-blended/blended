package blended.jms.utils

import javax.jms.{Message, Session}

@Deprecated
trait JMSMessageFactory[T] {
  def createMessage(session : Session, content : T) : Message
}
