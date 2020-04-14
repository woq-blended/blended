package blended.itestsupport.ldap

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

import akka.actor.{ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import javax.naming.directory.InitialDirContext

object LDAPAvailableCondition {
  def apply(env: Map[String, String], t: Option[FiniteDuration])(implicit system: ActorSystem) =
    AsyncCondition(LDAPChecker.props(env), "LDAPAvailableCondition", t)

}

private[ldap] object LDAPChecker {
  def props(env: Map[String, String]): Props = Props(new LDAPChecker(env))
}

private[ldap] class LDAPChecker(env: Map[String, String]) extends AsyncChecker {

  var connected: AtomicBoolean = new AtomicBoolean(false)
  var connecting: AtomicBoolean = new AtomicBoolean(false)

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def performCheck(condition: AsyncCondition): Future[Boolean] = {

    if (!connected.get() && !(connecting.get())) {
      connecting.set(true)

      val ldapEnv = new util.Hashtable[String, String]
      env.foreach { case (k, v) => ldapEnv.put(k, v) }

      try {
        val ctxt = new InitialDirContext(ldapEnv)
        connected.set(true)
        ctxt.close()
      } catch {
        case _: Throwable => connected.set(false)
      }

      connecting.set(false)
    }

    Future(connected.get())
  }
}
