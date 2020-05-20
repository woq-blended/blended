package blended.testsupport.pojosr

import akka.actor.{ActorSystem, Scheduler}
import blended.akka.http.internal.{AkkaHttpServerInfo, AkkaHttpServerJmxSupport}
import blended.jmx.{BlendedMBeanServerFacade, JmxObjectName}
import blended.testsupport.retry.Retry

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

trait AkkaHttpServerTestHelper extends AkkaHttpServerJmxSupport { this : PojoSrTestHelper =>

  override def objName: JmxObjectName = JmxObjectName(properties = Map("type" -> "AkkaHttpServer"))

  def akkaHttpInfo(registry : BlendedPojoRegistry) : AkkaHttpServerInfo = {

    implicit val timeout : FiniteDuration = 3.seconds
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
    implicit val eCtxt : ExecutionContext = system.dispatcher
    implicit val scheduler : Scheduler = system.scheduler
    val mbeanSvr : BlendedMBeanServerFacade = mandatoryService[BlendedMBeanServerFacade](registry)

    val f : Future[AkkaHttpServerInfo] = Retry.retry(delay = 1.second, retries = 3){
      readFromJmx(mbeanSvr).get
    }

    val info : AkkaHttpServerInfo = Await.result(f, 4.seconds)
    assert(info.port.nonEmpty)
    assert(info.port.forall(_ != 0))

    info
  }

  def plainServerUrl : BlendedPojoRegistry => String = registry => s"http://localhost:${akkaHttpInfo(registry).port.get}"

}
