package blended.updater.remote

import java.util.UUID

import scala.collection.JavaConverters._

import blended.persistence.PersistenceService
import blended.persistence.jdbc.{PersistedClassDao, PersistenceServiceJdbc}
import blended.testsupport.TestFile
import blended.testsupport.TestFile.DeleteWhenNoFailure
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config._
import blended.util.logging.Logger
import org.h2.jdbcx.{JdbcConnectionPool, JdbcDataSource}
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager

class RemoteUpdaterTest extends LoggingFreeSpec with TestFile {

  private[this] val log = Logger[this.type]

  val todoOverlays = List.empty[OverlayConfig]
  val todoOverlayRefs = Set.empty[OverlayRef]

  case class TestContext(remoteUpdater: RemoteUpdater, persitenceService: Option[PersistenceService] = None)

  def withEmptyRemoteUpdate(transient: Boolean)(f: TestContext => Unit): Unit = {
    transient match {
      case true =>
        val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
        f(TestContext(ru))
      case false =>
        withTestDir() { dir =>

          val ds0 = new JdbcDataSource();
          ds0.setURL(s"jdbc:h2:mem:${getClass().getSimpleName()}");
          ds0.setUser("admin");
          ds0.setPassword("admin");

          val ds = JdbcConnectionPool.create(ds0)

          val txMgr: PlatformTransactionManager = new DataSourceTransactionManager(ds)

          val dao: PersistedClassDao = new PersistedClassDao(ds)
          dao.init()

          val persistenceService = new PersistenceServiceJdbc(txMgr, dao)

          val csp = new PersistentContainerStatePersistor(persistenceService)

          val ru = new RemoteUpdater(
            new FileSystemRuntimeConfigPersistor(dir),
            csp,
            new FileSystemOverlayConfigPersistor(dir)
          )
          f(TestContext(ru, Some(persistenceService)))

          ds.dispose()

        }(DeleteWhenNoFailure)

    }
  }

  Seq(
    "transient" -> true,
    "JDBC-based" -> false
  ).foreach {
      case (name, transient) =>
        s"When persistence is ${name}" - {

          "initial empty state" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq())
            }
          }

          "adding a runtime config action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = AddRuntimeConfig(
                UUID.randomUUID().toString(),
                RuntimeConfig(
                  name = "test",
                  version = "1",
                  startLevel = 10,
                  defaultStartLevel = 10,
                  bundles = List(
                    BundleConfig(url = "mvn:test:test:1", startLevel = 0)
                  )
                )
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
            }
          }

          "adding a stage action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = StageProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
            }
          }

          "adding a second add runtime config action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
              val action1 = AddRuntimeConfig(
                UUID.randomUUID().toString(),
                RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = List(BundleConfig(url = "mvn:test:test:1", startLevel = 0)))
              )
              ru.addAction("1", action1)
              assert(ru.getContainerActions("1") === Seq(action1))
              val action2 = AddRuntimeConfig(
                UUID.randomUUID().toString(),
                RuntimeConfig(name = "test", version = "2", startLevel = 10, defaultStartLevel = 10, bundles = List(BundleConfig(url = "mvn:test:test:1", startLevel = 0)))
              )
              ru.addAction("1", action2)
              assert(ru.getContainerActions("1") === Seq(action1, action2))
            }
          }

          "adding a second stage action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = StageProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
              val action2 = StageProfile(
                UUID.randomUUID().toString(),
                "test", "2", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action2)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1, action2))
            }
          }

          "not adding a second but identical stage action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = StageProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
            }
          }

          "remove a stage action if container info reports already staged" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = StageProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))

              ctx.remoteUpdater.updateContainerState(ContainerInfo("1", Map(), List(), List(), 1L, Nil))
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))

              val profiles = List(
                Profile(name = "test", version = "1", overlaySet = OverlaySet(overlays = Set(), state = OverlayState.Valid))
              )
              ctx.remoteUpdater.updateContainerState(ContainerInfo("1", Map(), List(), profiles, 1L, Nil))
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq())
            }
          }

          "adding a update action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = ActivateProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
            }
          }

          "adding a second update action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = ActivateProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              val action2 = ActivateProfile(
                UUID.randomUUID().toString(),
                "test", "2", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action2)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1, action2))
            }
          }

          "not adding a second but identical update action" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = ActivateProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
            }
          }

          "remove an activation action if container info reports already activated" in {
            withEmptyRemoteUpdate(transient) { ctx =>
              val action1 = ActivateProfile(
                UUID.randomUUID().toString(),
                "test", "1", todoOverlayRefs
              )
              ctx.remoteUpdater.addAction("1", action1)
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq(action1))
              val profiles = List(
                Profile(name = "test", version = "1", overlaySet = OverlaySet(overlays = Set(), state = OverlayState.Active))
              )
              ctx.remoteUpdater.updateContainerState(ContainerInfo("1", Map(), List(), profiles, 1L, Nil))
              assert(ctx.remoteUpdater.getContainerActions("1") === Seq())
            }
          }

          "add a runtime config, an overlay and stage as new profile" in {
            withEmptyRemoteUpdate(transient) { ctx =>

              val conId = "1"

              val action1 = AddRuntimeConfig(
                UUID.randomUUID().toString(),
                RuntimeConfig(
                  name = "rc",
                  version = "1",
                  startLevel = 10,
                  defaultStartLevel = 10
                )
              )
              log.info(s"Add 1. action: ${action1}")
              ctx.remoteUpdater.addAction(conId, action1)
              ctx.persitenceService.map(p => assert(p.findAll(PersistentContainerStatePersistor.pClassName).size === 1))
              assert(ctx.remoteUpdater.getContainerActions(conId).size === 1)

              val action2 = AddOverlayConfig(
                UUID.randomUUID().toString(),
                OverlayConfig(
                  name = "oc",
                  version = "1"
                )
              )
              log.info(s"Add 2. action: ${action2}")
              ctx.remoteUpdater.addAction(conId, action2)
              ctx.persitenceService.map { p =>
                assert(p.findAll(PersistentContainerStatePersistor.pClassName).size === 1)
                val state = p.findByExample(PersistentContainerStatePersistor.pClassName, Map("containerId" -> conId).asJava)
                assert(state.size === 1)
                assert(Mapper.unmapContainerState(state.head).get.outstandingActions.size === 2)
              }
              log.info(s"state: ${ctx.remoteUpdater.getContainerState(conId)}")
              assert(ctx.remoteUpdater.getContainerActions(conId).size === 2)

              val action3 = StageProfile(
                UUID.randomUUID().toString(),
                profileName = "rc",
                profileVersion = "1",
                overlays = Set(OverlayRef("oc", "1"))
              )
              log.info(s"Add 3. action: ${action3}")
              ctx.persitenceService.map { p =>
                assert(p.findAll(PersistentContainerStatePersistor.pClassName).size === 1)
                assert(p.findByExample(PersistentContainerStatePersistor.pClassName, Map("containerId" -> conId).asJava).size === 1)
              }
              ctx.remoteUpdater.addAction(conId, action3)

              assert(ctx.remoteUpdater.getContainerActions(conId).size === 3)

            }
          }

        }

    }
}
