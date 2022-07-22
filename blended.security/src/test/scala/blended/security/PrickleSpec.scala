package blended.security

import blended.security.json.PrickleProtocol._
import org.scalatest.freespec.AnyFreeSpec
import prickle.{Pickle, Unpickle}

class PrickleSpec extends AnyFreeSpec {

  "The Pickler for security objects" - {

    "should (un)pickle BlendedPermission correctly" in {

      val permission = BlendedPermission(
        permissionClass = Some("container"),
        Map(
          "country" -> List("de", "bg")
        )
      )

      val json = Pickle.intoString(permission)
      val p2 = Unpickle[BlendedPermission].fromString(json).get

      assert(permission === p2)
    }

    "should (un)pickle BlendedPermissions correctly" in {

      val permission = BlendedPermission(
        permissionClass = Some("container"),
        Map(
          "country" -> List("de", "bg")
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
