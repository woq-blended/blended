package blended.jmx.internal

import javax.management.{MBeanServer, ObjectName}
import akka.actor.Actor
import blended.jmx.{NamingStrategy, ProductMBeanManager, NamingStrategyResolver}
import blended.util.logging.Logger
import akka.actor.Props
import blended.jmx.UpdateMBean
import blended.jmx.JmxObjectName
import blended.jmx.OpenMBeanMapper
import blended.jmx.RemoveMBean
import scala.util.control.NonFatal
import akka.actor.{ActorSystem, ActorRef}

class ProductMBeanManagerImpl(
  system : ActorSystem, 
  nsResolver : NamingStrategyResolver,
  svr : MBeanServer, 
  mapper : OpenMBeanMapper
) extends ProductMBeanManager {

  private var mgr : Option[ActorRef] = None

  override def start() : Unit = if (mgr.isEmpty) {
    mgr = Some(system.actorOf(MBeanManagerActor.props(svr, mapper, nsResolver)))
  }

  override def stop() : Unit = {
    mgr.foreach(system.stop)
    mgr = None
  }

  override def updateMBean(v: Product): Unit = mgr.foreach(_ ! UpdateMBean(v))
  override def removeMBean(v: Product): Unit = mgr.foreach(_ !RemoveMBean(v))

  private object MBeanManagerActor {
    def props(svr : MBeanServer, mapper : OpenMBeanMapper, resolver : NamingStrategyResolver) : Props = Props(new MBeanManagerActor(svr, mapper, resolver))
  }

  private class MBeanManagerActor(svr : MBeanServer, mapper : OpenMBeanMapper, resolver : NamingStrategyResolver) extends Actor {

    private val log : Logger = Logger(getClass().getName())

    override def preStart(): Unit = {
      context.become(running(Map.empty, Map.empty))
      log.debug(s"Started MBeanManager to watch for case class based MBeans")
    }

    override def receive: Actor.Receive = Actor.emptyBehavior

    private def running(mbeans : Map[String, OpenProductMBean], naming : Map[String, NamingStrategy]) : Receive = {

      case upd : UpdateMBean[_] => 
        getName(upd.v) match {
          case None => 
            log.warn(s"Could not determine name for [${upd.v}]")
          case Some(n) =>
            mbeans.get(n.objectName) match {
              case None => 
                log.debug(s"Registering MBean [$n] for [${upd.v}]")
                val mbean : OpenProductMBean = new OpenProductMBean(upd.v, n, mapper)
                // Make sure MBean does not exist in the Platform MBean Server
                try {
                  svr.unregisterMBean(new ObjectName(n.objectName))
                } catch {
                  case NonFatal(e) => // ignore
                }
                try {
                  svr.registerMBean(mbean, new ObjectName(n.objectName))
                  context.become(running(mbeans ++ Map(n.objectName -> mbean), naming))
                } catch {
                  case NonFatal(e) => 
                    log.warn(e)(s"Failed to register MBean [${upd.v}] with name [$n] : [${e.getMessage()}]")
                }
              case Some(bean) => 
                log.debug(s"Updating MBean [$n] with [${upd.v}]")
                bean.update(upd.v)
            }
        }

      case rem : RemoveMBean[_] => 
        getName(rem.v) match {
          case None => 
            log.warn(s"Could not determine name for [${rem.v}]")
          case Some(n) => 
            mbeans.get(n.objectName) match {
              case None => 
                log.debug(s"Ignoring request to unregister [${rem.v}] with name [$n] : MBean not registered")
              case Some(_) =>
                try {
                  svr.unregisterMBean(new ObjectName(n.objectName))
                  context.become(running(mbeans.filter{ case (k, _) => k != n.objectName}, naming))
                } catch {
                  case NonFatal(t) => 
                    log.warn(s"Failed to unregister MBean [${n.objectName}]")
                }
            }
            
        }  
    }

    private def getName(v : Product) : Option[JmxObjectName] = {
      val result : Option[JmxObjectName] = resolver.resolveNamingStrategy(v) match {
        case None => 
          None
        case Some(s) => 
          if (s.objectName.isDefinedAt(v)) { 
            Some(s.objectName(v)) 
          } else {
            None
          }
      }

      log.debug(s"Resolve object name of [$v] to [$result]")
      result
    }
  }
}