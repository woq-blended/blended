package blended.updater.remote

import org.scalatest.FreeSpec
import blended.updater.config._

class RemoteUpdaterTest extends FreeSpec {

  val todoOverlays = List.empty[OverlayConfig]
  val todoOverlayRefs = List.empty[OverlayRef]

  "initial empty state" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    assert(ru.getContainerActions("1") === Seq())
  }

  "adding a runtime config action" in {
    val ru = new RemoteUpdater(new TransientRuntimeConfigPersistor(), new TransientContainerStatePersistor(), new TransientOverlayConfigPersistor())
    val action1 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = List(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
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
    val action1 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = List(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
    ru.addAction("1", action1)
    assert(ru.getContainerActions("1") === Seq(action1))
    val action2 = AddRuntimeConfig(RuntimeConfig(name = "test", version = "2", startLevel = 10, defaultStartLevel = 10, bundles = List(BundleConfig(url = "mvn:test:test:1", startLevel = 0))))
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
    ru.updateContainerState(ContainerInfo("1", Map(), List(), profiles, 1L))
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
    ru.updateContainerState(ContainerInfo("1", Map(), List(), profiles, 1L))
    assert(ru.getContainerActions("1") === Seq())
  }

}