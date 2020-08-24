package blended.jmx.internal

import org.scalatest.matchers.should.Matchers
import blended.testsupport.scalatest.LoggingFreeSpecLike
import akka.testkit.TestKit
import akka.actor.ActorSystem
import blended.jmx.NamingStrategy
import blended.jmx.JmxObjectName
import java.lang.management.ManagementFactory
import blended.jmx.BlendedMBeanServerFacade
import scala.util.Failure
import scala.util.Success
import blended.jmx.IntAttributeValue
import blended.jmx.RegisterNamingStrategy
import blended.jmx.UpdateMBean
import javax.management.{MBeanServer, InstanceNotFoundException}
import blended.testsupport.retry.Retry
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.actor.Scheduler
import blended.jmx.RemoveMBean

case class Counter(
  name : String, 
  count : Int
)

class CounterName extends NamingStrategy {
  override val objectName: PartialFunction[Any, JmxObjectName] = {
    case c : Counter => new JmxObjectName(
      domain = "blended",
      properties = Map("type" -> "counter", "name" -> c.name)
    )
  }
}

class MBeanManagerSpec extends TestKit(ActorSystem("MBeanMgr"))
  with LoggingFreeSpecLike
  with Matchers {

  private val svr : MBeanServer = ManagementFactory.getPlatformMBeanServer()  
  private val mbeanSvr : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(svr)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val sched : Scheduler = system.scheduler

  system.actorOf(MBeanManager.props(svr, new OpenMBeanMapperImpl()))
  Thread.sleep(100)

  "The MBean Manager should" - {

    "Register/update MBeans upon publishing an arbitrary case class" in logException {

      val cnt : Counter = Counter("myCounter", 1)
      val st : NamingStrategy = new CounterName()
      
      system.eventStream.publish(RegisterNamingStrategy[Counter](st))
      system.eventStream.publish(UpdateMBean[Counter](cnt))

      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            info.attributes.value("count") should be (IntAttributeValue(1))
          case Failure(t) => 
            throw t
        }
      }

      system.eventStream.publish(UpdateMBean[Counter](cnt.copy(count = 5)))

      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            info.attributes.value("count") should be (IntAttributeValue(5))
          case Failure(t) => 
            throw t
        }
      }
    }

    "Remove a registered MBean upon request" in {

      val cnt : Counter = Counter("sndCounter", 1)
      val st : NamingStrategy = new CounterName()
      
      system.eventStream.publish(RegisterNamingStrategy[Counter](st))
      system.eventStream.publish(UpdateMBean[Counter](cnt))

      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            info.attributes.value("count") should be (IntAttributeValue(1))
          case Failure(t) => 
            throw t
        }
      }

      system.eventStream.publish(RemoveMBean[Counter](cnt))

      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            throw new Exception(s"Expected no bean for [$cnt]")
          case Failure(t) if t.isInstanceOf[InstanceNotFoundException ]=>
          case Failure(t) => throw t 
        }
      }
    }
  }  
}
