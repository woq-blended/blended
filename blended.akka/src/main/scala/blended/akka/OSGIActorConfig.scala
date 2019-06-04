package blended.akka

import akka.actor.ActorSystem
import blended.container.context.api.ContainerIdentifierService
import com.typesafe.config.Config
import org.osgi.framework.BundleContext

case class OSGIActorConfig(
  bundleContext : BundleContext,
  system : ActorSystem,
  config : Config,
  idSvc : ContainerIdentifierService
)

