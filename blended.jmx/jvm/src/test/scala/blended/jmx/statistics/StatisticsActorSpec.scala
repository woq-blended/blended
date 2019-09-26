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

  val server : MBeanServer = ManagementFactory.getPlatformMBeanServer()
  val mapper : OpenMBeanMapper = new OpenMBeanMapperImpl()
  val exporter : OpenMBeanExporter = new OpenMBeanExporterImpl(mapper) {
    override def mbeanServer: MBeanServer = server
  }

  object nextId {
    private[this] var _id : Int = 0

    def apply(): String = {
      _id += 1
      s"${_id}"
    }
  }

  private val objName : (String, Option[String]) => ObjectName = (comp, subComp) => {
    new ObjectName(JmxObjectName(properties =
      Map("component" -> comp) ++
        subComp.map(s => Map("subcomponent" -> s)).getOrElse(Map.empty)
    ).objectName)
  }

  s"The ${classOf[StatisticsActor]}" - {

    "should export a JMX bean for each name received via EventStream" in {
      system.actorOf(StatisticsActor.props(exporter))

      val names : Seq[(String, Option[String])] = Seq(("dispatcher", None), ("httproute", Some("foo")))

      names.foreach { case (comp, subComp) =>
        val on : ObjectName = objName(comp, subComp)
        intercept[InstanceNotFoundException] {
          server.getObjectInstance(on)
        }
      }

      names.foreach { case (comp, subComp) =>
        system.eventStream.publish(StatisticData(comp, subComp, nextId(), ServiceState.Started))
        Retry.unsafeRetry(10.milliseconds, 5) {
          val on : ObjectName = objName(comp, subComp)
          assert(server.getObjectInstance(on) != null)
          assert(server.getAttribute(on, "successCount") === 0L)
        }
      }

    }

    "should update an exported JMX bean" in {
      system.actorOf(StatisticsActor.props(exporter))
      val (comp, subComp) : (String, Option[String]) = ("foo", Some("bar"))

      val id = nextId()
      system.eventStream.publish(StatisticData(comp, subComp, id, ServiceState.Started))
      val on : ObjectName = objName(comp, subComp)

      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
      }

      system.eventStream.publish(StatisticData(comp, subComp, id, ServiceState.Completed))
      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 1L)
      }

    }

    "should update and record last failed" in {
      system.actorOf(StatisticsActor.props(exporter))
      val (comp, subComp) : (String, Option[String]) = ("dispatcher", None)

      val id : String = nextId()
      system.eventStream.publish(StatisticData(comp, subComp, id, ServiceState.Started))
      val on : ObjectName = objName(comp, subComp)
      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "lastFailed") === -1L)
      }

      system.eventStream.publish(StatisticData(comp, subComp, id, ServiceState.Failed))

      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "failedCount") === 1L)
        assert(server.getAttribute(on, "lastFailed").asInstanceOf[Long] > 0L)
      }

    }

  }

}
