package blended.updater

import java.io.File

import akka.actor.{ActorSystem, actorRef2Scala}
import akka.testkit.{ImplicitSender, TestKit}
import blended.testsupport.TestFile
import blended.testsupport.TestFile.DeleteNever
import blended.updater.config._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpecLike

class UpdaterTest
    extends TestKit(ActorSystem("updater-test"))
    with AnyFreeSpecLike
    with TestFile
    with ImplicitSender
    with BeforeAndAfterAll {

  implicit val deletePolicy = DeleteNever

  val dummyProfileActivator = new ProfileActivator {
    def apply(name: String, version: String): Boolean = true
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A minimal setup" - {

    "add config" in
      withTestFile("Bundle 1") { (bundle1) =>
        withTestDir() { baseDir =>
          val installBaseDir = new File(baseDir, "install")
          val updater = system.actorOf(
            Updater.props(installBaseDir, dummyProfileActivator, { () =>
              }, config = UpdaterConfig.default),
            s"updater-${nextId()}"
          )

          assert(!installBaseDir.exists())

          val config = ResolvedProfile(
            Profile(
              name = "test-with-1-framework-bundle",
              version = "1.0.0",
              bundles = List(
                BundleConfig(
                  url = bundle1.toURI().toString(),
                  sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
                  start = true,
                  startLevel = 0
                )),
              startLevel = 10,
              defaultStartLevel = 10
            ))

          {
            val addId = nextId()
            updater ! Updater.AddRuntimeConfig(addId, config.profile)
            expectMsgPF() {
              case Updater.OperationSucceeded(`addId`) => true
            }
            assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
            assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
            assert(
              new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
                Set("profile.conf"))
          }

          {
            val id = nextId()
            updater ! Updater.GetProfiles(id)
            expectMsgPF(hint = s"a result with id: [${id}] and empty profiles") {
              case Updater.Result(`id`, profiles: Set[_]) => true //  if profiles.isEmpty => true
            }
          }
        }
      }
    //  }

    "add conflicting config" in {
      withTestFile("Bundle 1") { (bundle1) =>
        withTestDir() { baseDir =>
          val installBaseDir = new File(baseDir, "install")
          val updater = system.actorOf(
            Updater.props(installBaseDir, dummyProfileActivator, { () =>
              }, config = UpdaterConfig.default),
            s"updater-${nextId()}"
          )

          assert(!installBaseDir.exists())

          {
            val id = nextId()
            updater ! Updater.GetProfiles(id)
            expectMsgPF() {
              case Updater.Result(`id`, profiles: Set[_]) if profiles.isEmpty => true
            }
          }

          val config = ResolvedProfile(
            Profile(
              name = "test-with-1-framework-bundle",
              version = "1.0.0",
              bundles = List(
                BundleConfig(
                  url = bundle1.toURI().toString(),
                  sha1Sum = "1316d3ef708f9059883a837ca833a22a6a5d1f6a",
                  start = true,
                  startLevel = 0
                )),
              startLevel = 10,
              defaultStartLevel = 10
            ))

          {
            val addId = nextId()
            updater ! Updater.AddRuntimeConfig(addId, config.profile)
            expectMsgPF() {
              case Updater.OperationSucceeded(`addId`) => true
            }
            assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
            assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
            assert(
              new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
                Set("profile.conf"))
          }

          //          {
          //            val id = nextId()
          //            updater ! GetAllProfiles(id)
          //            expectMsgPF(hint = s"id: ${id}") {
          //              case Updater.Result(`id`, profiles: Set[_]) if profiles.size == 1 => true
          //            }
          //          }

          {
            val config2 = config.profile.copy(startLevel = 20)
            val addId = nextId()
            updater ! Updater.AddRuntimeConfig(addId, config2)
            expectMsgPF() {
              case Updater.OperationFailed(`addId`, _) => true
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
            Updater.props(installBaseDir, dummyProfileActivator, { () =>
              }, config = UpdaterConfig.default),
            s"updater-${nextId()}"
          )

          assert(!installBaseDir.exists())

          val config = Profile(
            name = "test-with-1-framework-bundle",
            version = "1.0.0",
            bundles = List(
              BundleConfig(
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
            updater ! Updater.AddRuntimeConfig(addId, config)
            expectMsgPF() {
              case Updater.OperationSucceeded(`addId`) => true
            }
          }
          assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
          assert(
            new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
              Set("profile.conf"))

          {
            val stageId = nextId()
            updater ! Updater.StageProfile(stageId, config.name, config.version)
            expectMsgPF(hint = s"Waiting for: ${Updater.OperationSucceeded(stageId)}") {
              case Updater.OperationSucceeded(`stageId`) => true
            }
          }

          assert(installBaseDir.list().toSet === Set("test-with-1-framework-bundle"))
          assert(new File(installBaseDir, "test-with-1-framework-bundle").list.toSet === Set("1.0.0"))
          assert(
            new File(installBaseDir, "test-with-1-framework-bundle/1.0.0").list().toSet ===
              Set("profile.conf", "bundles"))
          assert(
            new File(installBaseDir, "test-with-1-framework-bundle/1.0.0/bundles").list().toSet ===
              Set("org.osgi.core-5.0.0.jar"))

        }
      }
    }

  }

  "A setup with 3 bundles" - {

    "Stage and install" in {

      val launchConfig = """
                           |""".stripMargin

      withTestFiles("Bundle 1", "Bundle 2", "Bundle 3", launchConfig) {
        (bundle1, bundle2, bundle3, launcherConfigFile) =>
          withTestDir() { baseDir =>
            val installBaseDir = new File(baseDir, "install")
            var restarted = false
            //          var curLaunchConfig = Option.empty[LauncherConfig]
            var curNameVersion = Option.empty[(String, String)]
            val updater = system.actorOf(
              Updater.props(
                installBaseDir,
                (name: String, version: String) => {
                  curNameVersion = Some(name -> version)
                  true
                },
                () => restarted = true,
                config = UpdaterConfig.default
              ),
              s"updater-${nextId()}"
            )

            {
              val config = ResolvedProfile(
                Profile(
                  name = "test-with-3-bundles",
                  version = "1.0.0",
                  bundles = List(
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
                    )
                  ),
                  startLevel = 10,
                  defaultStartLevel = 10
                ))

              {
                val addId = nextId()
                updater ! Updater.AddRuntimeConfig(addId, config.profile)

                expectMsgPF() {
                  case Updater.OperationSucceeded(`addId`) => true
                }
              }

              {
                val stageId = nextId()
                updater ! Updater.StageProfile(stageId, config.profile.name, config.profile.version)
                expectMsgPF(hint = s"waiting for: ${Updater.OperationSucceeded(stageId)}") {
                  case Updater.OperationSucceeded(`stageId`) => true
                }

                assert(installBaseDir.list().toSet === Set("test-with-3-bundles"))
                assert(new File(installBaseDir, "test-with-3-bundles").list().toSet === Set("1.0.0"))
                assert(
                  new File(installBaseDir, "test-with-3-bundles/1.0.0").list().toSet ===
                    Set("profile.conf", "bundles"))
                assert(
                  new File(installBaseDir, "test-with-3-bundles/1.0.0/bundles").list().toSet ===
                    Set("bundle1-1.0.0.jar", "bundle2-1.0.0.jar", "bundle3-1.0.0.jar"))
              }

              {
                val queryId = nextId()
                updater ! Updater.GetProfiles(queryId)
                expectMsgPF(hint = s"Query id: $queryId") {
                  case Updater.Result(`queryId`, profiles: Set[_]) =>
                    profiles.toList match {
                      case List(
                          LocalProfile(LocalRuntimeConfig(`config`, _), LocalProfile.Staged)
                          ) =>
                        true
                      case u => sys.error("unexpected: " + u)
                    }
                }
              }

              {
                assert(restarted === false)
                assert(curNameVersion === None)
                val reqId = nextId()
                updater ! Updater.ActivateProfile(reqId, "test-with-3-bundles", "1.0.0")
                expectMsgPF() {
                  case Updater.OperationSucceeded(`reqId`) => true
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
