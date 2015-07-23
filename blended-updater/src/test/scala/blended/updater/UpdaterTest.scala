package blended.updater

import java.io.File
import scala.collection.immutable.Seq
import org.osgi.framework.BundleContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpecLike
import akka.actor.ActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import blended.updater.test.TestSupport
import blended.updater.Updater.AddRuntimeConfig
import blended.updater.Updater.RuntimeConfigAdded
import blended.updater.Updater.StageRuntimeConfig
import blended.updater.Updater.StageRuntimeConfig
import blended.updater.Updater.RuntimeConfigStaged
import blended.updater.Updater.GetRuntimeConfigs
import blended.updater.Updater.RuntimeConfigs
import blended.updater.Updater.ActivateRuntimeConfig
import blended.updater.Updater.RuntimeConfigAdded
import blended.updater.Updater.StageRuntimeConfig
import blended.updater.Updater.GetProgress
import blended.updater.Updater.Progress
import blended.updater.Updater.RuntimeConfigStaged
import blended.updater.Updater.RuntimeConfigActivated
import blended.updater.config.RuntimeConfig
import blended.launcher.config.LauncherConfig
import blended.updater.test.TestSupport.DeleteWhenNoFailure
import blended.updater.test.TestSupport.DeleteNever
import blended.updater.config.BundleConfig
import blended.updater.Updater.RuntimeConfigAdditionFailed
import blended.updater.Updater.GetRuntimeConfigs
import blended.updater.Updater.RuntimeConfigs

class UpdaterTest
    extends TestKit(ActorSystem("updater-test"))
    with FreeSpecLike
    with TestSupport
    with ImplicitSender
    with BeforeAndAfterAll {

  implicit val deletePolicy = DeleteNever

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A minimal setup" - {

    "add config" in {
      withTestFile("Bundle 1") { (bundle1) =>
        withTestDir() { baseDir =>

          val installBaseDir = new File(baseDir, "install")
          val updater = system.actorOf(
            Updater.props(installBaseDir, { (n, v) => true }, { () => }, config = UpdaterConfig.default),
            s"updater-${nextId()}")

          assert(!installBaseDir.exists())

          val config = RuntimeConfig(
            name = "test-with-1-framework-bundle", version = "1.0.0",
            bundles = Seq(BundleConfig(
              url = bundle1.toURI().toString(),
              sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
              start = true,
              startLevel = 0
            )),
            startLevel = 10,
            defaultStartLevel = 10
          )

          {
            val addId = nextId()
            updater ! AddRuntimeConfig(addId, config)
            fishForMessage() {
              case RuntimeConfigAdded(`addId`) => true
            }
          }
          assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
            Set("profile.conf"))

          {
            val id = nextId()
            updater ! GetRuntimeConfigs(id)
            fishForMessage() {
              case Updater.RuntimeConfigs(`id`, Seq(), Seq(`config`), Seq()) => true
            }
          }
        }
      }
    }

    "add conflicting config" in {
      withTestFile("Bundle 1") { (bundle1) =>
        withTestDir() { baseDir =>

          val installBaseDir = new File(baseDir, "install")
          val updater = system.actorOf(
            Updater.props(installBaseDir, { (n, v) => true }, { () => }, config = UpdaterConfig.default),
            s"updater-${nextId()}")

          assert(!installBaseDir.exists())

          {
            val id = nextId()
            updater ! GetRuntimeConfigs(id)
            fishForMessage() {
              case Updater.RuntimeConfigs(`id`, Seq(), Seq(), Seq()) => true
            }
          }

          val config = RuntimeConfig(
            name = "test-with-1-framework-bundle", version = "1.0.0",
            bundles = Seq(BundleConfig(
              url = bundle1.toURI().toString(),
              sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
              start = true,
              startLevel = 0
            )),
            startLevel = 10,
            defaultStartLevel = 10
          )

          {
            val addId = nextId()
            updater ! AddRuntimeConfig(addId, config)
            fishForMessage() {
              case RuntimeConfigAdded(`addId`) => true
            }
            assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
            assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
            assert(new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
              Set("profile.conf"))
          }

          {
            val id = nextId()
            updater ! GetRuntimeConfigs(id)
            fishForMessage() {
              case Updater.RuntimeConfigs(`id`, Seq(), Seq(`config`), Seq()) => true
            }
          }

          {
            val config2 = config.copy(startLevel = 20)
            val addId = nextId()
            updater ! AddRuntimeConfig(addId, config2)
            fishForMessage() {
              case RuntimeConfigAdditionFailed(`addId`, _) => true
            }
          }

        }
      }
    }

    "stage" in {
      val launchConfig = """
      |""".stripMargin

      withTestFiles("Bundle 1", launchConfig) { (bundle1, launcherConfigFile) =>
        withTestDir() { baseDir =>

          val installBaseDir = new File(baseDir, "install")
          val updater = system.actorOf(
            Updater.props(installBaseDir, { (n, v) => true }, { () => }, config = UpdaterConfig.default),
            s"updater-${nextId()}")

          assert(!installBaseDir.exists())

          val config = RuntimeConfig(
            name = "test-with-1-framework-bundle", version = "1.0.0",
            bundles = Seq(BundleConfig(
              url = bundle1.toURI().toString(),
              sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
              start = true,
              startLevel = 0,
              jarName = "org.osgi.core-5.0.0.jar"
            )),
            startLevel = 10,
            defaultStartLevel = 10
          )

          {
            val addId = nextId()
            updater ! AddRuntimeConfig(addId, config)
            fishForMessage() {
              case RuntimeConfigAdded(`addId`) => true
            }
          }
          assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
            Set("profile.conf"))

          {
            val stageId = nextId()
            updater ! StageRuntimeConfig(stageId, config.name, config.version)
            fishForMessage() {
              case RuntimeConfigStaged(`stageId`) => true
            }
          }

          assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
            Set("profile.conf", "bundles"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle/1.0.0/bundles").list().toSet ===
            Set("org.osgi.core-5.0.0.jar"))

        }
      }
    }
  }

  "A setup with 3 bundles" - {

    "Stage and install" in {

      val launchConfig = """
      |""".stripMargin

      withTestFiles("Bundle 1", "Bundle 2", "Bundle 3", launchConfig) { (bundle1, bundle2, bundle3, launcherConfigFile) =>
        withTestDir() { baseDir =>

          val installBaseDir = new File(baseDir, "install")
          var restarted = false
          //          var curLaunchConfig = Option.empty[LauncherConfig]
          var curNameVersion = Option.empty[(String, String)]
          val updater = system.actorOf(
            Updater.props(
              installBaseDir,
              { (n, v) =>
                curNameVersion = Some(n -> v)
                true
              },
              () => restarted = true,
              config = UpdaterConfig.default),
            s"updater-${nextId()}"
          )

          {
            val config = RuntimeConfig(
              name = "test-with-3-bundles", version = "1.0.0",
              bundles = Seq(
                BundleConfig(
                  url = bundle1.toURI().toString(),
                  sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
                  start = true,
                  startLevel = 0,
                  jarName = "bundle1-1.0.0.jar"
                ),
                BundleConfig(
                  url = bundle2.toURI().toString(),
                  sha1Sum = "72cdfea44be8a153c44b9ed73129b6939bcc087d",
                  start = true,
                  startLevel = 2,
                  jarName = "bundle2-1.0.0.jar"
                ),
                BundleConfig(
                  url = bundle3.toURI().toString(),
                  sha1Sum = "a6d3a54eae9c63959997e55698c1b1e5ad097b06",
                  start = true,
                  startLevel = 2,
                  jarName = "bundle3-1.0.0.jar"
                )),
              startLevel = 10,
              defaultStartLevel = 10
            )

            {
              val addId = nextId()
              updater ! AddRuntimeConfig(addId, config)

              fishForMessage() {
                case RuntimeConfigAdded(`addId`) => true
              }
            }

            {
              val queryId = nextId()
              updater ! GetRuntimeConfigs(queryId)
              fishForMessage() {
                case RuntimeConfigs(`queryId`, Seq(), Seq(`config`), Seq()) => true
              }
            }

            {

              val stageId = nextId()
              updater ! StageRuntimeConfig(stageId, config.name, config.version)
              fishForMessage() {
                case RuntimeConfigStaged(`stageId`) => true
              }

              assert(installBaseDir.list().toSet === Set("test-with-3-bundles"))
              assert(new File(installBaseDir, "test-with-3-bundles").list().toSet === Set("1.0.0"))
              assert(new File(installBaseDir, "test-with-3-bundles/1.0.0").list().toSet ===
                Set("profile.conf", "bundles"))
              assert(new File(installBaseDir, "test-with-3-bundles/1.0.0/bundles").list().toSet ===
                Set("bundle1-1.0.0.jar", "bundle2-1.0.0.jar", "bundle3-1.0.0.jar"))
            }

            {
              val queryId = nextId()
              updater ! GetRuntimeConfigs(queryId)
              fishForMessage() {
                case RuntimeConfigs(`queryId`, Seq(`config`), Seq(), Seq()) => true
              }
            }

            {
              assert(restarted === false)
              assert(curNameVersion === None)
              val reqId = nextId()
              updater ! ActivateRuntimeConfig(reqId, "test-with-3-bundles", "1.0.0")
              fishForMessage() {
                case RuntimeConfigActivated(`reqId`) => true
              }
              // restart happens after the message, so we wait
              assert(curNameVersion === Some("test-with-3-bundles" -> "1.0.0"))
              Thread.sleep(500)
              assert(restarted === true)
            }
          }
        }
      }
    }
  }

}