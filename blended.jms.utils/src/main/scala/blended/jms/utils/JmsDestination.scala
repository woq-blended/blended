package blended.jms.utils

import javax.jms.{Destination, Session}

sealed trait  JmsDestination {
  val name : String
  val create : Session => Destination
}

final case class JmsTopic(override val name : String) extends JmsDestination {
  override val create: Session => Destination = session => session.createTopic(name)
}

final case class JmsDurableTopic(override val name : String, subscriberName : String) extends JmsDestination {
  override val create: Session => Destination = session => session.createTopic(name)
}

final case class JmsQueue(override val name : String) extends JmsDestination {
  override val create: Session => Destination = session => session.createQueue(name)
}
