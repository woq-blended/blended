package blended.security.boot

import java.security.Principal

class GroupPrincipal(group: String) extends Principal {
  override def getName(): String = group

  override def hashCode(): Int = group.hashCode()

  override def equals(other: scala.Any): Boolean = other match {
    case p : GroupPrincipal => group.equals(p.getName())
    case o => false
  }

  override def toString: String = s"GroupPrincipal($group)"
}
