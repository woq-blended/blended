package blended.security

case class BlendedPermission(
  // The name of the entity that is controlled, i.e. container
  permissionClass : String,
  // A description
  description : String = "",
  // The properties can further specify, which objects of the permissionClass are included
  // within the permission
  properties : Map[String, Seq[String]] = Map.empty
)
