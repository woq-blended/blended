package blended.jmx.internal

import org.scalatest.matchers.should.Matchers
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.jmx.NamingStrategy
import blended.jmx.JmxObjectName
import blended.jmx.BlendedMBeanServerFacade
import scala.util.Failure
import scala.util.Success
import blended.jmx.IntAttributeValue
import blended.testsupport.retry.Retry
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.actor.Scheduler
import blended.testsupport.pojosr.SimplePojoContainerSpec
import blended.testsupport.pojosr.PojoSrTestHelper
import org.osgi.framework.BundleActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.testsupport.BlendedTestSupport
import java.io.File
import domino.DominoActivator
import blended.jmx.NamingStrategyResolver
import blended.jmx.ProductMBeanManager
import akka.actor.ActorSystem
import javax.management.InstanceNotFoundException

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

class MBeanManagerSpec extends SimplePojoContainerSpec 
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "test" -> new DominoActivator() {
      whenBundleActive{
        new CounterName().providesService[NamingStrategy](NamingStrategyResolver.strategyClassNameProp -> classOf[Counter].getName())
      }
    }
  )

  "The MBean Manager should" - {

    "Register/update MBeans upon publishing an arbitrary case class" in logException {

      val cnt : Counter = Counter("myCounter", 1)
      val st : NamingStrategy = new CounterName()

      val system : ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val eCtxt : ExecutionContext = system.dispatcher
      implicit val sched : Scheduler = system.scheduler

      val mbeanSvr = mandatoryService[BlendedMBeanServerFacade](registry)
      val mgr : ProductMBeanManager = mandatoryService[ProductMBeanManager](registry)
      mgr.updateMBean(cnt)
      
      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            info.attributes.value("count") should be (IntAttributeValue(1))
          case Failure(t) => 
            throw t
        }
      }

      mgr.updateMBean(cnt.copy(count = 5))

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

      val system : ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val eCtxt : ExecutionContext = system.dispatcher
      implicit val sched : Scheduler = system.scheduler
      
      val cnt : Counter = Counter("myCounter", 1)
      val st : NamingStrategy = new CounterName()

      val mbeanSvr = mandatoryService[BlendedMBeanServerFacade](registry)
      val mgr : ProductMBeanManager = mandatoryService[ProductMBeanManager](registry)
      mgr.updateMBean(cnt)
      
      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            info.attributes.value("count") should be (IntAttributeValue(1))
          case Failure(t) => 
            throw t
        }
      }

      mgr.removeMBean(cnt)

      Retry.unsafeRetry(1.second, 5){
        mbeanSvr.mbeanInfo(st.objectName(cnt)) match {
          case Success(info) => 
            throw new Exception(s"Expected no bean for [$cnt]")
          case Failure(t) if t.isInstanceOf[InstanceNotFoundException]=>
          case Failure(t) => throw t 
        }
      }
    }
  }  
}
