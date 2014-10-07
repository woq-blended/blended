package de.woq.blended.itestsupport

import javax.jms.DeliveryMode

import akka.actor.ActorRef

package object jms {
  case class Connect(clientId: String)
  case class Connected(clientId: String)

  case object Disconnect
  case object Disconnected

  case class CreateProducer(destName: String)
  case class CreateConsumer(destName: String)
  case class CreateDurableSubscriber(topic: String, subScriberName: String)

  case object StopConsumer

  case class ProduceMessage(
    msgFactory: JMSMessageFactory,
    deliveryMode : Int = DeliveryMode.NON_PERSISTENT,
    priority : Int = 4,
    ttl : Long = 0
  )
  case object MessageProduced

  case class ProducerActor(producer: ActorRef)
  case class ConsumerActor(consumer: ActorRef)

  case object Unsubscribe
  case object ConsumerStopped

  case class AddConsumerToCounter(consumer: ActorRef)
  case class MessageCount(counter: ActorRef, count: Int)

}
