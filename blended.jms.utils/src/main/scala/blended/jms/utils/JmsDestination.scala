package blended.jms.utils

import javax.jms._

import scala.util.Try

object JmsDestination {

  private[utils] val destSeparator = ":"
  private[utils] val TOPICTAG = "topic"
  private[utils] val QUEUETAG = "queue"

  def create(destName : String) : Try[JmsDestination] = Try {

    val name = destName.split(":")

    name.length match {
      case 0 => throw new IllegalArgumentException("No destination name given")
      case 1 => JmsQueue(name(0))
      case 2 => name(0) match {
        case QUEUETAG => JmsQueue(name(1))
        case TOPICTAG => JmsTopic(name(1))
        case _        => throw new JMSException(s"String representation of JmsDestination must start with either [$QUEUETAG:] or [$TOPICTAG:]")
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

  def create(jmsDest : Destination) : Try[JmsDestination] = Try {
    jmsDest match {
      case t : Topic => JmsTopic(t.getTopicName())
      case q : Queue => JmsQueue(q.getQueueName())
      case _         => throw new IllegalArgumentException(s"Unknown destination type [${jmsDest.getClass().getName()}]")
    }
  }

  def asString(jmsDest : JmsDestination) : String = {
    jmsDest match {
      case q : JmsQueue         => q.name
      case t : JmsTopic         => s"$TOPICTAG$destSeparator${t.name}"
      case dt : JmsDurableTopic => s"$TOPICTAG$destSeparator:${dt.subscriberName}$destSeparator${dt.name}"
    }
  }
}

sealed trait JmsDestination {
  val name : String
  val create : Session => Destination
  val asString : String = JmsDestination.asString(this)
}

final case class JmsTopic(override val name : String) extends JmsDestination {
  override val create : Session => Destination = session => session.createTopic(name)
}

final case class JmsDurableTopic(override val name : String, subscriberName : String) extends JmsDestination {
  override val create : Session => Destination = session => session.createTopic(name)
}

final case class JmsQueue(override val name : String) extends JmsDestination {
  override val create : Session => Destination = session => session.createQueue(name)
}
