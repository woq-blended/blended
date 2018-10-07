package blended.jms.utils

import javax.jms.{Destination, JMSException, Session}

import scala.util.Try

object JmsDestination {

  private[this] val destSeparator = ":"
  private[this] val TOPICTAG = "topic"
  private[this] val QUEUETAG = "queue"

  def create(destName : String) : Try[JmsDestination] = Try {

    val name = destName.split(":")

    name.length match {
      case 0 => throw new IllegalArgumentException("No destination name given")
      case 1 => JmsQueue(name(0))
      case 2 => name(0) match {
        case QUEUETAG => JmsQueue(name(1))
        case TOPICTAG => JmsTopic(name(1))
        case _ => throw new JMSException(s"String representation of JmsDestination must start with either [$QUEUETAG:] or [$TOPICTAG:]")
      }
      case 3 =>
        if (name(0) == TOPICTAG) {
          JmsDurableTopic(name(2), name(1))
        } else {
          throw new IllegalArgumentException("Only names for durable Topics have 3 components")
        }

      case _ => throw new IllegalArgumentException(s"Illegal format for destination name [$name]")
    }
  }
}

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
