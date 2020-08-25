package blended.testsupport.pojosr

import akka.actor.{ActorSystem, Scheduler}
import blended.akka.http.internal.{AkkaHttpServerInfo, AkkaHttpServerJmxSupport}
import blended.jmx.BlendedMBeanServerFacade
import blended.testsupport.retry.Retry

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import blended.jmx.ProductMBeanManager

trait AkkaHttpServerTestHelper { this : PojoSrTestHelper =>

  def akkaHttpInfo(registry : BlendedPojoRegistry) : AkkaHttpServerInfo = {

    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
    implicit val eCtxt : ExecutionContext = system.dispatcher
    implicit val scheduler : Scheduler = system.scheduler

    val mbeanSvr : BlendedMBeanServerFacade = mandatoryService[BlendedMBeanServerFacade](registry)
    val mgr : ProductMBeanManager = mandatoryService[ProductMBeanManager](registry)

    val jmxSupport : AkkaHttpServerJmxSupport = new AkkaHttpServerJmxSupport(mgr)

    val f : Future[AkkaHttpServerInfo] = Retry.retry(delay = 1.second, retries = 3){
      jmxSupport.readFromJmx(mbeanSvr).get
    }

    val info : AkkaHttpServerInfo = Await.result(f, 4.seconds)
    assert(info.port.nonEmpty)
    assert(info.port.forall(_ != 0))

    info
  }

  def plainServerUrl : BlendedPojoRegistry => String = registry => s"http://localhost:${akkaHttpInfo(registry).port.get}"

}
