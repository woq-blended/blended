package blended.security.boot

import java.util
import javax.security.auth.Subject
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

import org.osgi.framework.BundleContext

object BlendedLoginModule {

  private[this] var context : Option[BundleContext] = None

  val propBundle = "blended.jaas.bundle"
  val propModule = "blended.jaas.Module"

  def init(bc: BundleContext) : Unit = context = Some(bc)

  def bundleContext : BundleContext = context match {
    case None => throw new IllegalStateException("The bundle context for the login module is not initialised")
    case Some(bc) => bc
  }
}

class BlendedLoginModule extends LoginModule {

  import BlendedLoginModule._

  private[this] val notInitialised = "Login module is not initialised."

  private[this] var target : Option[LoginModule] = None

  override def initialize(subject: Subject, callbackHandler: CallbackHandler, sharedState: util.Map[String, _], options: util.Map[String, _]): Unit = {

    val newOptions = options

    val moduleClass : Option[String] = Option(newOptions.remove(propModule).toString())
    val moduleBundle : Option[String] = Option(newOptions.remove(propBundle).toString())

    (moduleClass, moduleBundle) match {
      case (None, _) => throw new IllegalStateException(s"Option [${propBundle}] must be set to the name of the factory service bundle.")
      case (_, None) => throw new IllegalStateException(s"Option [${propModule}] must be set to the name of the factory service.")
      case (Some(clazz), Some(bundleName)) =>
        Option(bundleContext.getBundle(bundleName)) match {
          case None => throw new IllegalStateException(s"Bundle [${bundleName}] not found.")
          case Some(bundle) =>
            try {
              target = Some(bundle.loadClass(clazz).asInstanceOf[LoginModule])
              target.foreach(t => t.initialize(
                subject,
                callbackHandler,
                sharedState,
                newOptions
              ))
            } catch {
              case e : Exception => throw new IllegalStateException(s"Could not load Login Module [${clazz}] from bundle [${bundleName}]")
            }
        }
    }
  }

  override def logout(): Boolean = target match {
    case None => throw new LoginException(notInitialised)
    case Some(t) => t.logout()
  }

  override def abort(): Boolean = target match {
    case None => throw new LoginException(notInitialised)
    case Some(t) => t.abort()
  }

  override def commit(): Boolean = target match {
    case None => throw new LoginException(notInitialised)
    case Some(t) => t.commit()
  }

  override def login(): Boolean = target match {
    case None => throw new LoginException(notInitialised)
    case Some(t) => t.login()
  }

}
