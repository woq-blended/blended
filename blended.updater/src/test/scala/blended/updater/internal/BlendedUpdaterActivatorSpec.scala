package blended.updater.internal

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.{ActorSelection, ActorSystem}
import blended.akka.internal.BlendedAkkaActivator
import blended.launcher.TestBrandingPropsMutator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.updater.config.Profile
import org.osgi.framework.BundleActivator

class BlendedUpdaterActivatorSpec extends SimplePojoContainerSpec with LoggingFreeSpecLike with PojoSrTestHelper {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  val akkaBundleName = classOf[BlendedAkkaActivator].getPackage().getName().replaceAll("[.]internal", "")
  val bundleName = classOf[BlendedUpdaterActivator].getPackage().getName().replaceAll("[.]internal", "")

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    akkaBundleName -> new BlendedAkkaActivator()
//    bundleName -> new BlendedUpdaterActivator()
  )

  s"The bundle ${bundleName}" - {
    "in an non-updateable environment" - {
      "should fail a bundle start" in {
        val fail = withStartedBundle(registry)(bundleName, new BlendedUpdaterActivator()) { _ =>
          Try { () }
        }
        assert(fail.isFailure)
      }
    }
    "in an updateable environment" - {

      "should register a bundle actor into the Akka system" in {
        val actorSystem = mandatoryService[ActorSystem](registry, None)

        val sel: ActorSelection = actorSystem.actorSelection(s"/user/${bundleName}")
        val shouldFail = Await.ready(sel.resolveOne(1.second), 2.seconds)
        assert(shouldFail.isCompleted && shouldFail.value.get.isFailure)

        TestBrandingPropsMutator.setBrandingProperties(
          Map(
            Profile.Properties.PROFILE_NAME -> "test",
            Profile.Properties.PROFILE_VERSION -> "1.0",
            Profile.Properties.PROFILES_BASE_DIR -> (baseDir + "/profile")
          ))
        val result = withStartedBundle(registry)(bundleName, new BlendedUpdaterActivator()) { _ =>
          Try {
            val sel: ActorSelection = actorSystem.actorSelection(s"/user/${bundleName}")
            Await.result(sel.resolveOne(1.second), 2.seconds)
          }
        }
        assert(result.isSuccess, s"But was: ${result}")
      }
    }
  }

}
