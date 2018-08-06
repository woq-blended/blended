package blended.security

case class BlendedPermission(
  // The name of the entity that is controlled, i.e. container
  permissionClass : String,
  // A description
  description : String = "",
  // The properties can further specify, which objects of the permissionClass are included
  // within the permission
  properties : Map[String, Seq[String]] = Map.empty
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
}
