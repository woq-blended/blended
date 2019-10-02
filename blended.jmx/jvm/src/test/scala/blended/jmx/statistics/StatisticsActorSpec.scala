package blended.jmx.statistics

import java.lang.management.ManagementFactory

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit
import blended.jmx.{JmxObjectName, OpenMBeanExporter, OpenMBeanMapper}
import blended.jmx.internal.{OpenMBeanExporterImpl, OpenMBeanMapperImpl}
import blended.testsupport.retry.Retry
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.{InstanceNotFoundException, MBeanServer, ObjectName}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class StatisticsActorSpec
  extends TestKit(ActorSystem("StatisticsActorSpec"))
    with LoggingFreeSpecLike {

  private implicit val scheduler : Scheduler = system.scheduler
  private implicit val executionContext: ExecutionContext = system.dispatcher

  private val server : MBeanServer = ManagementFactory.getPlatformMBeanServer()
  private val mapper : OpenMBeanMapper = new OpenMBeanMapperImpl()
  private val exporter : OpenMBeanExporter = new OpenMBeanExporterImpl(mapper) {
    override def mbeanServer: MBeanServer = server
  }

  private val retryDelay : FiniteDuration = 100.milliseconds
  private val retries : Int = 5

  private val objName : (String, Option[String]) => ObjectName = (comp, subComp) => {
    new ObjectName(JmxObjectName(properties =
      Map("component" -> comp) ++
        subComp.map(s => Map("subcomponent" -> s)).getOrElse(Map.empty)
    ).objectName)
  }

  s"The ${classOf[StatisticsActor]}" - {

    "should export a JMX bean for each name received via EventStream" in {
      val actor = system.actorOf(StatisticsActor.props(exporter))

      val names : Seq[(String, Option[String])] = Seq(("dispatcher", None), ("httproute", Some("foo")))

      names.foreach { case (comp, subComp) =>
        val on : ObjectName = objName(comp, subComp)
        intercept[InstanceNotFoundException] {
          server.getObjectInstance(on)
        }
      }

      names.foreach { case (comp, subComp) =>

        val reporter : ServiceInvocationReporter = new ServiceInvocationReporter(comp, subComp)
        reporter.invoked()

        Retry.unsafeRetry(retryDelay, retries) {
          val on : ObjectName = objName(comp, subComp)
          assert(server.getObjectInstance(on) != null)
          assert(server.getAttribute(on, "successCount") === 0L)
          assert(server.getAttribute(on, "inflight") === 1L)
        }
      }

      system.stop(actor)
    }

    "should update an exported JMX bean" in {
      val actor = system.actorOf(StatisticsActor.props(exporter))
      val (comp, subComp) = ("foo", Some("bar"))
      val reporter = new ServiceInvocationReporter(comp, subComp)

      Thread.sleep(100)
      val id = reporter.invoked()

      val on : ObjectName = objName(comp, subComp)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
      }

      reporter.completed(id)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 1L)
        assert(server.getAttribute(on, "failedCount") === 0L)
        assert(server.getAttribute(on, "inflight") === 0L)
        assert(server.getAttribute(on, "lastFailed") === "")
      }

      system.stop(actor)
    }

    "should update and record last failed" in {
      val actor = system.actorOf(StatisticsActor.props(exporter))
      val (comp, subComp) : (String, Option[String]) = ("failing", None)
      val reporter = new ServiceInvocationReporter(comp, subComp)

      Thread.sleep(100)

      val id : String = reporter.invoked()
      val on : ObjectName = objName(comp, subComp)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "lastFailed") === "")
      }

      reporter.failed(id)

      Retry.unsafeRetry(retryDelay, retries) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "inflight") === 0L)
        assert(server.getAttribute(on, "failedCount") === 1L)
        assert(server.getAttribute(on, "lastFailed").asInstanceOf[String].length() > 0L)
      }

      system.stop(actor)
    }
  }
}
