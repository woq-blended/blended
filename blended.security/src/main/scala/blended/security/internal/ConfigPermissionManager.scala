package blended.security.internal

import blended.security.boot.GroupPrincipal
import blended.security.{BlendedPermission, BlendedPermissionManager, BlendedPermissions}
import com.typesafe.config.{Config, ConfigObject}
import javax.security.auth.Subject

import scala.jdk.CollectionConverters._
import scala.collection.{immutable => sci}

class ConfigPermissionManager(obj : ConfigObject) extends BlendedPermissionManager {

  val permissions : Map[String, BlendedPermissions] = obj.keySet().asScala.toList.map { jaasGrp =>
    val cfgPermissions : BlendedPermissions =
      BlendedPermissions(obj.toConfig().getConfigList(jaasGrp).asScala.toList.map(permission)).merged
    (jaasGrp, cfgPermissions)
  }.toMap

  override def permissions(subject : Subject) : BlendedPermissions = {
    val groups = subject.getPrincipals(classOf[GroupPrincipal]).asScala.toSeq.map(_.getName()).filter { g =>
      permissions.isDefinedAt(g)
    }

    val allPermissions = permissions.filter { p => groups.contains(p._1) }.values.map(_.granted).flatten.toList
    BlendedPermissions(allPermissions).merged
  }

  private[this] def permission(config : Config) : BlendedPermission = {
    val clazz = config.getString("permissionClass")
    val props : Map[String, sci.Seq[String]] = if (config.hasPath("properties")) {
      config.getObject("properties").keySet().asScala.map { k =>
        (k, config.getConfig("properties").getStringList(k).asScala.toList)
      }.toMap
    } else {
      Map.empty
    }
    BlendedPermission(Some(clazz), props)
  }
}

