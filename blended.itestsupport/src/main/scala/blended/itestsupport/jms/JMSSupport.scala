package blended.itestsupport.jms

import javax.jms._
trait JMSSupport {

  val TOPICTAG = "topic:"
  val QUEUETAG = "queue:"

  def jmsConnection : Connection

  def withSession(f: (Session => Unit)) : Unit = {

    var session : Option[Session] = None
    try {
      session = Some(jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE))
      f(session.get)
    } finally {
      session.foreach { s =>
        s.close() }
    }
  }

  def destination(session: Session, destName: String) : Destination = {
    if (destName.startsWith(TOPICTAG))
      session.createTopic(destName.substring(TOPICTAG.length))
    else if (destName.startsWith(QUEUETAG))
      session.createQueue(destName.substring(QUEUETAG.length))
    else
      session.createQueue(destName)
  }

}
