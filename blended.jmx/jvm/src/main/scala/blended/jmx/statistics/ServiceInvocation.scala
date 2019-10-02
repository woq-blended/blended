package blended.jmx.statistics

import java.util.UUID

import akka.actor.ActorSystem

sealed trait ServiceInvocationEvent {
  val id : String
  val timestamp : Long
}

case class ServiceInvocationStarted(
  override val id : String,
  override val timestamp : Long = System.currentTimeMillis(),
  component : String,
  subComponent : Option[String] = None
) extends ServiceInvocationEvent

case class ServiceInvocationCompleted(
  override val id : String,
  override val timestamp : Long = System.currentTimeMillis(),
) extends ServiceInvocationEvent

case class ServiceInvocationFailed(
  override val id : String,
  override val timestamp : Long = System.currentTimeMillis(),
) extends ServiceInvocationEvent

class ServiceInvocationReporter(
  component : String,
  subComponent : Option[String]
)(implicit system : ActorSystem) {

  def invoked() : String = {
    val id : String = UUID.randomUUID().toString()

    val event : ServiceInvocationEvent = ServiceInvocationStarted(
      id = id,
      component = component,
      subComponent = subComponent
    )

    system.eventStream.publish(event)

    id
  }

  def completed(id : String) : Unit = {
    system.eventStream.publish(
      ServiceInvocationCompleted(id)
    )
  }

  def failed(id : String) : Unit = {
    system.eventStream.publish(
      ServiceInvocationFailed(id)
    )
  }
}
