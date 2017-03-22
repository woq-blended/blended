package blended.jms.utils

import javax.jms.{Message, Session}

trait JMSMessageFactory {

  def createMessage(session: Session, content: Option[Any] = None) : Message

  def createMessage(session: Session) : Message = createMessage(session, None)
}