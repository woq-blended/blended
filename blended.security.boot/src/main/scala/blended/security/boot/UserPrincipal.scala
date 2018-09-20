package blended.security.boot

import java.security.Principal

class UserPrincipal(user: String) extends Principal {
  override def getName(): String = user

  override def hashCode(): Int = user.hashCode()

  override def equals(other: Any): Boolean = other match {
    case p : UserPrincipal => user.equals(p.getName())
    case _ => false
  }

  override def toString: String = s"UserPrincipal($user)"
}
