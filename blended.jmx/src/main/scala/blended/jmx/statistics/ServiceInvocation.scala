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
  subComponents : Map[String, String] = Map.empty
) extends ServiceInvocationEvent

case class ServiceInvocationCompleted(
  override val id : String,
  override val timestamp : Long = System.currentTimeMillis(),
) extends ServiceInvocationEvent

case class ServiceInvocationFailed(
  override val id : String,
  override val timestamp : Long = System.currentTimeMillis(),
) extends ServiceInvocationEvent

object ServiceInvocationReporter {

  def invoked(
    component : String,
    subComponents : Map[String, String],
    id : String
  )(implicit system : ActorSystem) : String = {
    val event : ServiceInvocationEvent = ServiceInvocationStarted(
      id = id,
      component = component,
      subComponents = subComponents
    )

    system.eventStream.publish(event)

    id
  }

  def invoked(component : String, subComponents : Map[String, String])(implicit system: ActorSystem) : String =
    invoked(component, subComponents, UUID.randomUUID().toString())

  def completed(id : String)(implicit system: ActorSystem) : Unit = {
    system.eventStream.publish(ServiceInvocationCompleted(id))
  }

  def failed(id : String)(implicit system: ActorSystem) : Unit = {
    system.eventStream.publish(ServiceInvocationFailed(id))
  }
}
