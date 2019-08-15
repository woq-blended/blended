package blended.mgmt.service.jmx.internal

import akka.actor.Props
import blended.akka.{OSGIActor, OSGIActorConfig}
import javax.management.MBeanServer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ServiceJmxCollector {
  def props(cfg : OSGIActorConfig, svcConfig : ServiceJmxConfig, server : MBeanServer) : Props =
    Props(new ServiceJmxCollector(cfg, svcConfig, server))
}

class ServiceJmxCollector(cfg : OSGIActorConfig, svcConfig : ServiceJmxConfig, server : MBeanServer) extends OSGIActor(cfg) {

  case object Tick

  private implicit val eCtxt : ExecutionContext = cfg.system.dispatcher

  val analyser = new ServiceJmxAnalyser(server, svcConfig)

  override def preStart() : Unit = {
    super.preStart()
    self ! Tick
  }

  override def receive : Receive = {
    case Tick =>
      log.debug("Refreshing Service Information from JMX")
      analyser.anaºlyse().foreach(info => cfg.system.eventStream.publish(info))
      context.system.scheduler.scheduleOnce(svcConfig.interval.seconds, self, Tick)
  }

}
