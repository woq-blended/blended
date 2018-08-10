package blended.security

import blended.security.json.PrickleProtocol._
import org.scalatest.FreeSpec
import prickle.{Pickle, Unpickle}

class PrickleSpec extends FreeSpec {

  "The Pickler for security objects should" - {

    "(un)pickle BlendedPermission correctly" in {

      val permission = BlendedPermission(
        permissionClass = Some("container"),
        Map(
          "country" -> Seq("de", "bg")
        )
      )

      val json = Pickle.intoString(permission)
      val p2 = Unpickle[BlendedPermission].fromString(json).get

      assert(permission === p2)
    }

    "(un)pickle BlendedPermissions correctly" in {

      val permission = BlendedPermission(
        permissionClass = Some("container"),
        Map(
          "country" -> Seq("de", "bg")
        )
      )

      val permissions = BlendedPermissions(List(permission))

      val json = Pickle.intoString(permissions)
      val unpickled = Unpickle[BlendedPermissions].fromString(json).get

      val p2 = unpickled.granted.head

      assert(permission === p2)
    }
  }
}
