package blended.jmx.statistics

import blended.testsupport.retry.Retry
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.{InstanceNotFoundException, MBeanServer, ObjectName}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import blended.testsupport.pojosr.SimplePojoContainerSpec
import blended.testsupport.pojosr.PojoSrTestHelper
import org.scalatest.matchers.should.Matchers
import blended.testsupport.BlendedTestSupport
import org.osgi.framework.BundleActivator
import domino.DominoActivator
import blended.jmx.NamingStrategy
import blended.jmx.NamingStrategyResolver
import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.internal.BlendedJmxActivator
import java.io.File
import blended.jmx.JmxObjectName
import akka.actor.ActorSystem
import akka.actor.Scheduler

class ServiceStatisticsActorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "test" -> new DominoActivator() {
      whenBundleActive{
        new ServiceNamingStrategy().providesService[NamingStrategy](NamingStrategyResolver.strategyClassNameProp -> classOf[ServicePublishEntry].getName())
      }
    }
  )

  private val retryDelay : FiniteDuration = 100.milliseconds
  private val retries : Int = 5

  private val objName : (String, Map[String, String]) => ObjectName = (comp, subComp) => {
    new ObjectName(JmxObjectName(properties =
      Map("component" -> comp) ++ subComp
    ).objectName)
  }

  s"The ${classOf[ServiceStatisticsActor]}" - {

    "should export a JMX bean for each name received via EventStream" in {
      val server : MBeanServer = mandatoryService[MBeanServer](registry)

      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val scheduler : Scheduler = system.scheduler
      implicit val executionContext: ExecutionContext = system.dispatcher


      val names : Seq[(String, Map[String, String])] =
        Seq(
          ("dispatcher", Map.empty),
          ("httproute", Map("context" -> "foo"))
        )

      names.foreach { case (comp, subComp) =>
        val on : ObjectName = objName(comp, subComp)
        intercept[InstanceNotFoundException] {
          server.getObjectInstance(on)
        }
      }

      names.foreach { case (comp, subComp) =>

        ServiceInvocationReporter.invoked(comp, subComp)

        Retry.unsafeRetry(retryDelay, retries) {
          val on : ObjectName = objName(comp, subComp)
          assert(server.getObjectInstance(on) != null)
          assert(server.getAttribute(on, "successCount") === 0L)
          assert(server.getAttribute(on, "inflight") === 1L)
        }
      }
    }

    "should update an exported JMX bean" in {
      val server : MBeanServer = mandatoryService[MBeanServer](registry)

      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val scheduler : Scheduler = system.scheduler
      implicit val executionContext: ExecutionContext = system.dispatcher

      val (comp, subComp) = ("foo", Map("type" -> "bar"))
      val id : String = ServiceInvocationReporter.invoked(comp, subComp)

      val on : ObjectName = objName(comp, subComp)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
      }

      ServiceInvocationReporter.completed(id)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 1L)
        assert(server.getAttribute(on, "failedCount") === 0L)
        assert(server.getAttribute(on, "inflight") === 0L)
        assert(server.getAttribute(on, "lastFailed") === "")
      }
    }

    "should update and record last failed" in {
      val server : MBeanServer = mandatoryService[MBeanServer](registry)

      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
      implicit val scheduler : Scheduler = system.scheduler
      implicit val executionContext: ExecutionContext = system.dispatcher

      val (comp, subComp) : (String, Map[String, String]) = ("failing", Map.empty)

      val id : String = ServiceInvocationReporter.invoked(comp, subComp)
      val on : ObjectName = objName(comp, subComp)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "lastFailed") === "")
      }

      ServiceInvocationReporter.failed(id)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "inflight") === 0L)
        assert(server.getAttribute(on, "failedCount") === 1L)
        assert(server.getAttribute(on, "lastFailed").asInstanceOf[String].length() > 0L)
      }
    }
  }
}
