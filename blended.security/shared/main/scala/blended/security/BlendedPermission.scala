package blended.security

import blended.security.json.PrickleProtocol._
import prickle.Unpickle

import scala.util.Try

/**
  *
  * @param permissionClass The name of the entity that is controlled, i.e. container. If <code>None</code>,
  *                        the permission won't grant access to any object.
  * @param description A human readable description of the permission, only used for display purposes.
  * @param properties Properties restricting the access to controlled object, further specified in the
  *                   <code>allows</code> method.
  */
case class BlendedPermission(
  permissionClass : Option[String],
  description: String = "",
  properties: Map[String, Seq[String]] = Map.empty
) {

  def allows(other: BlendedPermission) : Boolean = {

    def checkProperties : Boolean = {

      val props : Seq[(String, Seq[String], Option[Seq[String]])]= properties.map( p => (p._1, p._2, other.properties.get(p._1))).toSeq

      val contained : Seq[Boolean]= props.map{ p =>
        p._3 match {
          case None => false
          case Some(l) => l.forall(s => p._2.contains(s))
        }
      }


      contained.forall(x => x)
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
          .map { case (k, v) => (k, (v ++ other.properties.getOrElse(k, Seq.empty)).distinct) }

        BlendedPermission(
          permissionClass = permissionClass,
          description = description,
          properties = props
        )
      }
    }
  }
}

object BlendedPermissions {
  def fromJson(json: String) : Try[BlendedPermissions] = Unpickle[BlendedPermissions].fromString(json)
}

case class BlendedPermissions(granted: Seq[BlendedPermission])
