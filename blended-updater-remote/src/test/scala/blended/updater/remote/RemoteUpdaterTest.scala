package blended.updater.remote

import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ServiceInfo
import blended.updater.config.BundleConfig
import blended.updater.config.RuntimeConfig
import org.scalatest.FreeSpec
import scala.collection.immutable
import blended.mgmt.base.StageProfile
import blended.mgmt.base.ActivateProfile

class RemoteUpdaterTest extends FreeSpec {

  "initial empty state" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    assert(ru.getContainerActions("1") === Seq())
  }

  "adding a stage action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = StageProfile(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "adding a second stage action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = StageProfile(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val action2 = StageProfile(RuntimeConfig(name = "test", version = "2", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action2)
    assert(ru.getContainerActions("1") === Seq(action1, action2))
  }

  "not adding a second but identical stage action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = StageProfile(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "remove a stage action if container info reports already staged" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = StageProfile(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.updateContainerState(ContainerInfo("1", Map(), immutable.Seq(ServiceInfo("/blended.updater", System.currentTimeMillis(), 100000L, Map("profiles.valid" -> "test-1")))))
    assert(ru.getContainerActions("1") === Seq())
  }

  "adding a update action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = ActivateProfile("test", "1")
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "adding a second update action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = ActivateProfile("test", "1")
    ru.addAction("1", action1)
    val action2 = ActivateProfile("test", "2")
    ru.addAction("1", action2)
    assert(ru.getContainerActions("1") === Seq(action1, action2))
  }

  "not adding a second but identical update action" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = ActivateProfile("test", "1")
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "remove an activation action if container info reports already activated" in {
    val ru = new RemoteUpdater with TransientPersistor {}
    val action1 = ActivateProfile("test", "1")
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.updateContainerState(ContainerInfo("1", Map(), immutable.Seq(ServiceInfo("/blended.updater", System.currentTimeMillis(), 100000L, Map("profile.active" -> "test-1")))))
    assert(ru.getContainerActions("1") === Seq())
  }

}