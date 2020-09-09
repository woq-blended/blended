package blended.mgmt.agent.internal

import java.io.File

import scala.concurrent.Await

import akka.actor.{ActorNotFound, ActorSelection, ActorSystem}
import blended.akka.internal.BlendedAkkaActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class MgmtAgentActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  val akkaBundleName = classOf[BlendedAkkaActivator].getPackage().getName().replaceAll("[.]internal", "")
  val bundleName = classOf[MgmtAgentActivator].getPackage().getName().replaceAll("[.]internal", "")

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    akkaBundleName -> new BlendedAkkaActivator(),
    bundleName -> new MgmtAgentActivator()
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  s"The bundle ${bundleName}" - {

    "should register a MgmtReporter actor into the Akka system" in {
      val actorSystem = mandatoryService[ActorSystem](registry, None)
      val sel: ActorSelection = actorSystem.actorSelection(s"/user/${bundleName}")
      Await.result(sel.resolveOne(1.second), 2.seconds)
    }

    "should de-register the MgmtReporter actor after bundle stop" in {
      val actorSystem = mandatoryService[ActorSystem](registry, None)
      val sel: ActorSelection = actorSystem.actorSelection(s"/user/${bundleName}")
      Await.result(sel.resolveOne(1.second), 2.seconds)

      bundleByName(registry)(bundleName).get.stop()

      intercept[ActorNotFound] {
        Await.result(sel.resolveOne(1.second), 2.seconds)
      }
    }
  }

}
