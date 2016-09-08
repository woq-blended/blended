package blended.akka

import akka.actor.ActorSystem
import com.typesafe.config.Config
import blended.container.context.ContainerIdentifierService
import org.osgi.framework.BundleContext

case class OSGIActorConfig (
  bundleContext: BundleContext,
  system: ActorSystem,
  config: Config,
  idSvc: ContainerIdentifierService
)

