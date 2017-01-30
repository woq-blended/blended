package blended.itestsupport.jms

import javax.jms.{Connection, ConnectionFactory}

import akka.actor.{Actor, ActorLogging, Props}
import blended.itestsupport.jms.protocol._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object JMSConnectorActor {
  def apply(cf: ConnectionFactory) = new JMSConnectorActor(cf)
}

class JMSConnectorActor(cf: ConnectionFactory) extends Actor with ActorLogging {

  implicit val ctxt = context.dispatcher
  var postStopActions = List.empty[() => Unit]

  private def addPostStopAction(f : () => Unit) : Unit = {
    postStopActions = f :: postStopActions
  }

  private def clearPostStopActions() : Unit = {
    postStopActions = List.empty[() => Unit]
  }

  def receive = disconnected

  def disconnected : Receive = {
    case Connect(clientId, user, pwd) => {
      val result = createConnection(clientId, user, pwd)

      sender ! result
    }
  }

  def connected(connection: Connection) : Receive =  {
    case Disconnect => {
      connection.close()
      context.become(disconnected)
      clearPostStopActions()
      sender ! Right(Disconnected)
    }
    case CreateProducer(destName, msgCounter) => {
      log.info(s"Creating a producer for [${destName}] with clientId [${connection.getClientID}]")
      val producer = context.actorOf(Props(Producer(connection, destName, msgCounter)))
      sender ! ProducerActor(producer)
    }
    case CreateConsumer(destName, msgCounter) => {
      log.info(s"Creating a consumer for [${destName}] with clientId [${connection.getClientID}]")
      val consumer = context.actorOf(Props(Consumer(connection, destName, None, msgCounter)))
      consumer.forward(Consumer.ConsumerCreated)
    }
    case CreateDurableSubscriber(destName,subscriberName,msgCounter) => {
      log.info(s"Creating a durable subscriber for [${destName}] with clientId [${connection.getClientID}] and subscriberName")
      val consumer = context.actorOf(Props(Consumer(connection, destName, Some(subscriberName), msgCounter)))
      consumer.forward(Consumer.ConsumerCreated)
    }
  }

  override def postStop(): Unit = {

    postStopActions.foreach(f => f)
    clearPostStopActions()
    super.postStop()
  }

  private def createConnection(
    clientId: String, user: Option[String], password: Option[String]
  ) : Either[JMSCaughtException, Connected] = {

    try {
      val connection = if (user.isDefined && password.isDefined)
        cf.createConnection(user.get, password.get)
      else
        cf.createConnection()

      log.debug(s"Connection [${clientId}] created ...")

      connection.setClientID(clientId)
      connection.start()
      context.become(connected(connection))

      val f = (() => { c: Connection =>
        log.debug(s"Closing connection [${clientId}]")
        c.close()
      }.apply(connection))

      addPostStopAction(f)

      Right(Connected(clientId))
    } catch {
      case t: Throwable =>
        log.warning(s"Couldn't create JMS connection [${clientId}], ${t.getMessage()}")
        Left(JMSCaughtException(t))
    }
  }
}
