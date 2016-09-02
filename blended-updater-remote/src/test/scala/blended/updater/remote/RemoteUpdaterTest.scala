package blended.updater.remote

import scala.collection.immutable

import org.scalatest.FreeSpec

import blended.mgmt.base.ActivateProfile
import blended.mgmt.base.AddRuntimeConfig
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ServiceInfo
import blended.mgmt.base.StageProfile
import blended.mgmt.base.OverlaySet
import blended.mgmt.base.OverlayState
import blended.updater.config.BundleConfig
import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayRef
import blended.updater.config.RuntimeConfig
import blended.mgmt.base.Profile

class RemoteUpdaterTest extends FreeSpec {

  val todoOverlays = Set[OverlayConfig]()
  val todoOverlayRefs = Set[OverlayRef]()

  "initial empty state" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    assert(ru.getContainerActions("1") === Seq())
  }

  "adding a runtime config action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "adding a stage action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = StageProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "adding a second add runtime config  action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val action2 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "2", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action2)
    assert(ru.getContainerActions("1") === Seq(action1, action2))
  }

  "adding a second stage action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = StageProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val action2 = StageProfile("test", "2", todoOverlayRefs)
    ru.addAction("1", action2)
    assert(ru.getContainerActions("1") === Seq(action1, action2))
  }

  "not adding a second but identical stage action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = StageProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "remove a stage action if container info reports already staged" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = StageProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val profiles = List(
      Profile(name = "test", version = "1", overlays = List(OverlaySet(overlays = List(), state = OverlayState.Valid)))
    )
    ru.updateContainerState(ContainerInfo("1", Map(), List(), profiles))
    assert(ru.getContainerActions("1") === Seq())
  }

  "adding a update action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = ActivateProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "adding a second update action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = ActivateProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    val action2 = ActivateProfile("test", "2", todoOverlayRefs)
    ru.addAction("1", action2)
    assert(ru.getContainerActions("1") === Seq(action1, action2))
  }

  "not adding a second but identical update action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = ActivateProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
  }

  "remove an activation action if container info reports already activated" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = ActivateProfile("test", "1", todoOverlayRefs)
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val profiles = List(
      Profile(name = "test", version = "1", overlays = List(OverlaySet(overlays = List(), state = OverlayState.Active)))
    )
    ru.updateContainerState(ContainerInfo("1", Map(), List(), profiles))
    assert(ru.getContainerActions("1") === Seq())
  }

}