package blended.updater

import java.io.File
import org.osgi.framework.BundleContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpecLike
import org.scalatest.mock.MockitoSugar
import akka.actor.ActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import blended.updater.test.TestSupport
import blended.updater.UpdaterActor.StageUpdate
import blended.updater.UpdaterActor.StageUpdate
import blended.updater.UpdaterActor.StageUpdate
import scala.collection.immutable.Seq
import blended.updater.UpdaterActor.StageUpdateProgress
import blended.updater.UpdaterActor.StageUpdate
import blended.updater.UpdaterActor.StageUpdateFinished

class UpdaterActorTest
  extends TestKit(ActorSystem("updater-test"))
  with FreeSpecLike
  with TestSupport
  with ImplicitSender
  with BeforeAndAfterAll
  with MockitoSugar {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Stage a minimal setup" in {
    withTestFile("Bundle 1") { bundle1 =>

      withTestDir() { baseDir =>
        val bundleContext = mock[BundleContext]
        val configDir = new File(baseDir, "config")
        val installBaseDir = new File(baseDir, "install")
        val updater = system.actorOf(UpdaterActor.props(bundleContext, configDir.getPath(), installBaseDir), "updater")

        val stageId = nextId()
        val config = RuntimeConfig(
          name = "test", version = "1.0.0",
          framework = BundleConfig(
            url = bundle1.toURI().toString(),
            sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
            start = true,
            startLevel = Some(0),
            jarName = "org.osgi.core-5.0.0.jar"
          ),
          bundles = Seq(),
          startLevel = 10
        )
        updater ! StageUpdate(stageId, testActor, config)
        expectMsg(StageUpdateProgress(stageId, 0))
        expectMsg(StageUpdateProgress(stageId, 0))
        expectMsg(StageUpdateProgress(stageId, 100))
        fishForMessage() {
          case msg: StageUpdateFinished => true
        }
      }
    }

  }

}