package blended.itestsupport.jms

import javax.jms.{Message, Session}

import scala.util.Random

trait JMSMessageFactory {

  def createMessage(session: Session, content: Option[Any] = None) : Message
}

class FixedSizeMessageFactory(size: Int) extends JMSMessageFactory {

  private val rnd = new Random()

  private lazy val body = {
    val result = new Array[Byte](size)
    rnd.nextBytes(result)
    result
  }


  override def createMessage(session: Session, content: Option[Any]) = {
    val m = session.createBytesMessage()
    m.writeBytes(body)
    m
  }
}

class TextMessageFactory extends JMSMessageFactory {
  override def createMessage(session: Session, content: Option[Any]) = {
    val body = content match {
      case None => "None"
      case Some(m) => m.toString
    }
    session.createTextMessage(body)
  }
}
