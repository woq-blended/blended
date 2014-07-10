package de.woq.blended.itestsupport.jmx

import javax.management.remote.{JMXConnectorFactory, JMXConnector}

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.jmx.protocol._

trait BlendedJMXConnector extends Actor with ActorLogging{ this : JMXUrlProvider =>

  private var connector : Option[JMXConnector] = None
  private var requests : List[(ActorRef, Any)] = List.empty

  def connected : Receive = LoggingReceive {
    case Disconnect => {
      connector.foreach(_.close())
      connector = None
      sender ! Disconnected
      context become disconnected
    }
  }

  def disconnected : Receive = LoggingReceive {
    case Connect => {
      connector = Some(JMXConnectorFactory.connect(serviceUrl))
      connector.get.connect()
      requests.reverse.foreach{ case (s, m) => self.tell(m, s) }
      sender ! Connected
      context become connected
    }
    case r => requests = (sender, r) :: requests
  }

  def receive = disconnected

  override def postStop() {
    connector.foreach(_.close())
  }
}
