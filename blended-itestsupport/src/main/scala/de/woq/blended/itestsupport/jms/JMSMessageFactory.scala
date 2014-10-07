package de.woq.blended.itestsupport.jms

import javax.jms.{Session, Message}

trait JMSMessageFactory {

  def createMessage(session: Session) : Message
}
