package blended.mgmt.ws.internal

import akka.actor.{Actor, ActorRef, Props}
import blended.jmx.BlendedMBeanServerFacade
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object JmxRefreshActor {

  def props(dispatcher: ActorRef, facade: BlendedMBeanServerFacade) : Props =
    Props(new JmxRefreshActor(dispatcher, facade))
}

class JmxRefreshActor(dispatcher : ActorRef, mBeanSrv : BlendedMBeanServerFacade) extends Actor {

  private val log : Logger = Logger[JmxRefreshActor]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  case object Tick

  override def preStart(): Unit = self ! Tick

  override def receive: Receive = {
    case Tick =>
      mBeanSrv.mbeanNames() match {
        case Success(l) =>
          log.debug(s"Found [${l.size}] MBean names")
          //dispatcher ! NewData(l)
          context.system.scheduler.scheduleOnce(1.second, self, Tick)
        case Failure(t) =>
          log.warn(t)(s"Error getting MBean names : [${t.getMessage()}]")
          context.system.scheduler.scheduleOnce(1.second, self, Tick)
      }
  }
}
