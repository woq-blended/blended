package de.woq.blended.itestsupport.jms

import javax.jms.{Connection, ConnectionFactory}

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.jms.protocol._

object JMSConnectorActor {
  def apply(cf: ConnectionFactory) = new JMSConnectorActor(cf)
}

class JMSConnectorActor(cf: ConnectionFactory) extends Actor with ActorLogging {

  def receive = disconnected

  def disconnected : Receive = LoggingReceive {
    case Connect(clientId, user, pwd) => {
      val connection = if (user.isDefined && pwd.isDefined)
        cf.createConnection(user.get, pwd.get)
      else
        cf.createConnection()

      connection.setClientID(clientId)
      connection.start()
      context.become(connected(connection))
      sender ! Connected(clientId)
    }
  }

  def connected(connection: Connection) : Receive = LoggingReceive {
    case Disconnect => {
      connection.close()
      context.become(disconnected)
      sender ! Disconnected
    }
    case CreateProducer(destName, msgCounter) => {
      val producer = context.actorOf(Props(Producer(connection, destName, msgCounter)))
      sender ! ProducerActor(producer)
    }
    case CreateConsumer(destName, msgCounter) => {
      val consumer = context.actorOf(Props(Consumer(connection, destName, None, msgCounter)))
      sender ! ConsumerActor(consumer)
    }
    case CreateDurableSubscriber(destName,subscriberName,msgCounter) => {
      val consumer = context.actorOf(Props(Consumer(connection, destName, Some(subscriberName), msgCounter)))
      sender ! ConsumerActor(consumer)
    }
  }
}
