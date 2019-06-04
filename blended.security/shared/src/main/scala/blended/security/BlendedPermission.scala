package blended.security

import blended.security.json.PrickleProtocol._
import prickle.Unpickle

import scala.collection.{immutable => sci}
import scala.util.Try

/**
 *
 * @param permissionClass The name of the entity that is controlled, i.e. container. If <code>None</code>,
 *                        the permission won't grant access to any object.
 * @param properties Properties restricting the access to controlled object, further specified in the
 *                   <[[allows()]] method.
 */
case class BlendedPermission(
  permissionClass : Option[String],
  properties : Map[String, sci.Seq[String]] = Map.empty
) {

  def allows(other : BlendedPermission) : Boolean = {

    def checkProperties : Boolean = {

      val props : Seq[(String, Seq[String], Option[Seq[String]])] = properties.map(p => (p._1, p._2, other.properties.get(p._1))).toSeq

      props.forall { p =>
        p._3 match {
          case None    => false
          case Some(l) => l.forall(s => p._2.contains(s))
        }
      }
    }

    permissionClass.equals(other.permissionClass) && checkProperties
  }

  /**
   * Merge this permission with another permission.
   *
   * If one of the permissions to be merged has an empty permission class, the result wil always
   * have an empty permission class as well, in other words the result will not grant access to
   * any object.
   *
   * Also if the permission classes of the two permissions are different, the result will have
   * an empty permission class.
   *
   * @param other The permission to merged.
   * @return The resulting permission.
   */
  def merge(other : BlendedPermission) : BlendedPermission = {
    (permissionClass, other.permissionClass) match {
      case (None, _) => BlendedPermission(None)
      case (_, None) => BlendedPermission(None)
      case (Some(c1), Some(c2)) => if (c1 != c2) {
        BlendedPermission(None)
      } else {

        // if for any given property one of the permissions does not have a restriction defined
        // the result will also not restrict on that property.

        val props = properties
          // we need the properties defined in both permissions
          .filterKeys(k => other.properties.get(k).isDefined)
          // then we combine the properties of both permissions
          .map { case (k, v) => (k, (v ++ other.properties.getOrElse(k, sci.Seq.empty)).distinct) }

        BlendedPermission(
          permissionClass = permissionClass,
          properties = props
        )
      }
    }
  }
}

object BlendedPermissions {
  def fromJson(json : String) : Try[BlendedPermissions] = Unpickle[BlendedPermissions].fromString(json)
}

case class BlendedPermissions(granted : sci.Seq[BlendedPermission] = sci.Seq.empty[BlendedPermission]) {

  /**
   * Merge all permissions in a sequence from left to right
   * @param permissions The permissions to merged
   * @return The merged permission
   *
   * @see [[BlendedPermission.merge()]]
   */
  def merge(permissions : Seq[BlendedPermission]) : BlendedPermission = permissions match {
    case Seq()          => BlendedPermission(None)
    case h +: Seq()     => h
    case h +: s +: rest => merge(h.merge(s) +: rest)
  }

  /**
   * Return a BlendedPermissions object where all permissions with the same permissionClass
   * are merged
   */
  lazy val merged : BlendedPermissions = {
    val grouped = granted.groupBy(_.permissionClass)
    BlendedPermissions(grouped.values.map(merge).toList)
  }

  def allows(p : BlendedPermission) : Boolean = granted.exists { g => g.allows(p) }
}
