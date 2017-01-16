package blended.mgmt.service.jmx.internal

import javax.management.MBeanServer

import akka.actor.{Cancellable, Props}
import blended.akka.{OSGIActor, OSGIActorConfig}

import scala.concurrent.duration._

object ServiceJmxCollector {
  def props(cfg: OSGIActorConfig, svcConfig: ServiceJmxConfig, server : MBeanServer) =
    Props(new ServiceJmxCollector(cfg, svcConfig, server))
}

class ServiceJmxCollector(cfg: OSGIActorConfig, svcConfig : ServiceJmxConfig, server: MBeanServer) extends OSGIActor(cfg) {

  case object Tick

  implicit val eCtxt = cfg.system.dispatcher

  var timer : Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
    timer = Some(cfg.system.scheduler.schedule(10.millis, svcConfig.interval.seconds, self, Tick))
  }

  override def postStop(): Unit = {
    timer.foreach(_.cancel())
    super.postStop()
  }

  override def receive: Receive = {
    case Tick =>
      log.info("Refreshing Service Information from JMX")
  }
}
