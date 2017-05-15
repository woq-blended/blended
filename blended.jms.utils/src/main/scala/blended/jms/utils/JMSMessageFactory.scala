package blended.jms.utils

import javax.jms.{Message, Session}

trait JMSMessageFactory[T] {
  def createMessage(session: Session, content: T) : Message
}