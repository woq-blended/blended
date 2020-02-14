package blended.akka

import akka.actor.ActorSystem
import blended.container.context.api.ContainerContext
import com.typesafe.config.Config
import org.osgi.framework.BundleContext

case class OSGIActorConfig(
  bundleContext : BundleContext,
  system : ActorSystem,
  config : Config,
  ctContext : ContainerContext
)

