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
import blended.updater.Updater.StageUpdate
import blended.updater.Updater.StageUpdate
import blended.updater.Updater.StageUpdate
import scala.collection.immutable.Seq
import blended.updater.Updater.StageUpdateProgress
import blended.updater.Updater.StageUpdate
import blended.updater.Updater.StageUpdateFinished

class UpdaterTest
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
        val updater = system.actorOf(Updater.props(bundleContext, configDir.getPath(), installBaseDir), s"updater-${nextId()}")

        val stageId = nextId()
        val config = RuntimeConfig(
          name = "test-with-1-framework-bundle", version = "1.0.0",
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

        assert(installBaseDir.list().toSet ===
          Set("test-with-1-framework-bundle-1.0.0"))
        assert(new File(installBaseDir, "test-with-1-framework-bundle-1.0.0").list().toSet ===
          Set("org.osgi.core-5.0.0.jar"))

      }
    }
  }

  "Stage a setup with 3 bundles" in {
    withTestFiles("Bundle 1", "Bundle 2", "Bundle 3") { (bundle1, bundle2, bundle3) =>
      withTestDir() { baseDir =>

        val bundleContext = mock[BundleContext]
        val configDir = new File(baseDir, "config")
        val installBaseDir = new File(baseDir, "install")
        val updater = system.actorOf(Updater.props(bundleContext, configDir.getPath(), installBaseDir), s"updater-${nextId()}")

        val stageId = nextId()
        val config = RuntimeConfig(
          name = "test-with-3-bundles", version = "1.0.0",
          framework = BundleConfig(
            url = bundle1.toURI().toString(),
            sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
            start = true,
            startLevel = Some(0),
            jarName = "bundle1-1.0.0.jar"
          ),
          bundles = Seq(
            BundleConfig(
              url = bundle2.toURI().toString(),
              sha1Sum = "72cdfea44be8a153c44b9ed73129b6939bcc087d",
              start = true,
              startLevel = Some(0),
              jarName = "bundle2-1.0.0.jar"
            ),
            BundleConfig(
              url = bundle3.toURI().toString(),
              sha1Sum = "a6d3a54eae9c63959997e55698c1b1e5ad097b06",
              start = true,
              startLevel = Some(0),
              jarName = "bundle3-1.0.0.jar"
            )),
          startLevel = 10
        )
        updater ! StageUpdate(stageId, testActor, config)

        fishForMessage() {
          case msg: StageUpdateProgress => false
          case msg: StageUpdateFinished => true
        }

        assert(installBaseDir.list().toSet ===
          Set("test-with-3-bundles-1.0.0"))
        assert(new File(installBaseDir, "test-with-3-bundles-1.0.0").list().toSet ===
          Set("bundle1-1.0.0.jar", "bundle2-1.0.0.jar", "bundle3-1.0.0.jar"))
      }
    }
  }

}