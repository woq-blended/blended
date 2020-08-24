package blended.jmx.internal

import javax.management.{MBeanServer, ObjectName}
import akka.actor.Actor
import blended.jmx.NamingStrategy
import blended.jmx.RegisterNamingStrategy
import blended.util.logging.Logger
import blended.jmx.MBeanUpdateEvent
import akka.actor.Props
import blended.jmx.UpdateMBean
import blended.jmx.JmxObjectName
import blended.jmx.OpenMBeanMapper
import blended.jmx.RemoveMBean
import scala.util.control.NonFatal

object MBeanManager {
  def props(svr : MBeanServer, mapper : OpenMBeanMapper) : Props = Props(new MBeanManager(svr, mapper))
}

class MBeanManager(svr : MBeanServer, mapper : OpenMBeanMapper) extends Actor {

  private val log : Logger = Logger(getClass().getName())

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[MBeanUpdateEvent])
    context.become(running(Map.empty, Map.empty))
    log.info(s"Started MBeanManager to watch for case class based MBeans")
  }

  override def receive: Actor.Receive = Actor.emptyBehavior

  private def running(mbeans : Map[String, OpenProductMBean], naming : Map[String, NamingStrategy]) : Receive = {
    case reg : RegisterNamingStrategy[_] => 
      val k : String = reg.cTag.runtimeClass.getName()
      if (!naming.isDefinedAt(k)) {
        log.info(s"Registering Naming Strategy for [$k]")
        context.become(running(mbeans, naming ++ Map(k -> reg.ns)))
      } else {
        log.info(s"Ignoring new Naming Strategy for [$k]")
      }

    case upd : UpdateMBean[_] => 
      getName(upd.v, naming) match {
        case None => 
          log.warn(s"Could not determine name for [${upd.v}]")
        case Some(n) =>
          mbeans.get(n.objectName) match {
            case None => 
              log.info(s"Registering MBean [$n] for [${upd.v}]")
              val mbean : OpenProductMBean = new OpenProductMBean(upd.v, n, mapper)
              try {
                svr.registerMBean(mbean, new ObjectName(n.objectName))
                context.become(running(mbeans ++ Map(n.objectName -> mbean), naming))
              } catch {
                case NonFatal(e) => 
                  log.warn(s"Failed to register MBean [${upd.v}] with name [$n]")
              }
            case Some(bean) => 
              log.info(s"Updating MBean [$n] with [${upd.v}]")
              bean.update(upd.v)
          }
      }

    case rem : RemoveMBean[_] => 
      getName(rem.v, naming) match {
        case None => 
          log.warn(s"Could not determine name for [${rem.v}]")
        case Some(n) => 
          mbeans.get(n.objectName) match {
            case None => 
              log.info(s"Ignoring request to unregister [${rem.v}] with name [$n] : MBean not registered")
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

  private def getName(v : Product, naming : Map[String, NamingStrategy]) : Option[JmxObjectName] = 
    naming.get(v.getClass().getName()).map(s => s.objectName(v))

}
