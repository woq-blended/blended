package blended.akka.http.internal

import java.io.File

import akka.actor.{ActorSystem, Scheduler}
import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.{BlendedMBeanServerFacade, JmxObjectName}
import blended.jmx.internal.BlendedJmxActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.retry.Retry
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class HttpActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with AkkaHttpServerJmxSupport{

  private implicit val timeout : FiniteDuration = 3.seconds

  override def objName: JmxObjectName = JmxObjectName(properties = Map("type" -> "AkkaHttpServer"))

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator()
  )

  private val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private val mbeanSvr : BlendedMBeanServerFacade = mandatoryService[BlendedMBeanServerFacade](registry)(None)

  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val scheduler : Scheduler = system.scheduler

  "The Akka Http Activator should" - {

    "start a HTTP server based on Akka HTTP" in {
      val f : Future[AkkaHttpServerInfo] = Retry.retry(delay = 1.second, retries = 3){
        readFromJmx(mbeanSvr).get
      }

      val info : AkkaHttpServerInfo = Await.result(f, 4.seconds)
      assert(info.port.nonEmpty)
      assert(info.port.forall(_ != 0))
      assert(info.routes.isEmpty)
    }
  }
}
