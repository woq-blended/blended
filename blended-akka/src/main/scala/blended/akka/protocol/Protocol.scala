package blended.akka {

  import akka.actor.ActorRef
  import com.typesafe.config.Config
  import org.osgi.framework.BundleContext

  package protocol {
    
    // A bundle has been started via ActorSystemAware
    case class BundleActorStarted(bundleId: String)

    //
    // Protocol for the EvenSource trait
    //
    case class RegisterListener(listener: ActorRef)
    case class DeregisterListener(listener: ActorRef)
    case class SendEvent[T](event : T)
  }
}
