package blended.security

import java.security.Principal

class UserPrincipal(user: String) extends Principal {
  override def getName(): String = user

  override def hashCode(): Int = user.hashCode()

  override def equals(other: scala.Any): Boolean = other match {
    case p : UserPrincipal => user.equals(p.getName())
    case o => false
  }

  override def toString: String = s"UserPrincipal($user)"
}
