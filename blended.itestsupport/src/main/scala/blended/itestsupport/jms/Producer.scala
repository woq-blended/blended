package blended.itestsupport.jms

import javax.jms._

import akka.actor._
import akka.event.LoggingReceive
import blended.util.protocol.IncrementCounter
import blended.itestsupport.jms.protocol._
import blended.jms.utils.JMSSupport

object Producer {

  def apply(connection: Connection, destName: String, msgCounter: Option[ActorRef]) =
    new Producer(connection, destName, msgCounter)
}

class Producer(connection: Connection, destName: String, msgCounter: Option[ActorRef])
  extends JMSSupport with Actor with ActorLogging {

  override def receive = LoggingReceive {

    case produce : ProduceMessage => {
      withSession { session =>
        log.debug(s"Sending [${produce.count}] message(s) to [${destName}]")
        val dest = destination(session, destName)
        val producer = session.createProducer(null)
        val msg = produce.msgFactory.createMessage(session, produce.content)
        for(i <- 1 to produce.count) {
          producer.send(
            dest,
            msg,
            produce.deliveryMode,
            produce.priority,
            produce.ttl
          )
          msgCounter.foreach { counter => counter ! new IncrementCounter() }
        }
      }(connection)
      sender ! MessageProduced
    }
  }
}

