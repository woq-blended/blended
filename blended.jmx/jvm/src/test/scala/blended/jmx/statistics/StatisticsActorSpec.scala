package blended.jmx.statistics

import java.lang.management.ManagementFactory

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.jmx.internal.{OpenMBeanExporterImpl, OpenMBeanMapperImpl}
import blended.testsupport.retry.Retry
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.{InstanceNotFoundException, MBeanServer, ObjectName}
import scala.concurrent.duration._

class StatisticsActorSpec
  extends TestKit(ActorSystem("StatisticsActorSpec"))
    with LoggingFreeSpecLike {

  implicit val scheduler = system.scheduler
  implicit val executionContext = system.dispatcher

  val server = ManagementFactory.getPlatformMBeanServer()
  val mapper = new OpenMBeanMapperImpl()
  val exporter = new OpenMBeanExporterImpl(mapper) {
    override def mbeanServer: MBeanServer = server
  }

  object nextId {
    private[this] var _id = 0

    def apply() = {
      _id += 1
      s"${_id}"
    }
  }

  s"The ${classOf[StatisticsActor]}" - {

    "should export a JMX bean for each name received via EventStream" in {
      system.actorOf(StatisticsActor.props(exporter))

      val names = Seq("blended.example:name=Data1", "org.example:name=Data2")

      names.foreach { name =>
        val on = new ObjectName(name)
        intercept[InstanceNotFoundException] {
          server.getObjectInstance(on)
        }
      }

      names.foreach { name =>
        system.eventStream.publish(StatisticData(name, nextId(), ServiceState.Started))
        Retry.unsafeRetry(10.milliseconds, 5) {
          val on = new ObjectName(name)
          assert(server.getObjectInstance(on) != null)
          assert(server.getAttribute(on, "successCount") === 0L)
        }
      }

    }

    "should update an exported JMX bean" in {
      system.actorOf(StatisticsActor.props(exporter))
      val name = "blended.example:name=Data2"

      val id = nextId()
      system.eventStream.publish(StatisticData(name, id, ServiceState.Started))
      val on = new ObjectName(name)

      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
      }

      system.eventStream.publish(StatisticData(name, id, ServiceState.Completed))
      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 1L)
      }

    }

    "should update and record last failed" in {
      system.actorOf(StatisticsActor.props(exporter))
      val name = "blended.example:name=Data3"

      val id = nextId()
      system.eventStream.publish(StatisticData(name, id, ServiceState.Started))
      val on = new ObjectName(name)
      Retry.unsafeRetry(10.milliseconds, 5) {
        val instance = server.getObjectInstance(on)
        assert(instance != null)
        assert(server.getAttribute(on, "successCount") === 0L)
        assert(server.getAttribute(on, "lastFailed") === -1L)
      }

      system.eventStream.publish(StatisticData(name, id, ServiceState.Failed))
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
