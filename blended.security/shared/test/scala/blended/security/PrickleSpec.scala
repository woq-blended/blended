package blended.security

import org.scalatest.{FreeSpec, Matchers}
import prickle.{Pickle, Unpickle}
import blended.security.json.PrickleProtocol._

class PrickleSpec extends FreeSpec {

  "The Pickler for security objects should" - {

    "(un)pickle BlendedPermissions correctly" in {

      val permission = BlendedPermission(
        permissionClass = "container",
        description = "A permission controlling read access to container objects",
        Map(
          "country" -> Seq("de", "bg")
        )
      )

      val json = Pickle.intoString(permission)
      val p2 = Unpickle[BlendedPermission].fromString(json).get

      assert(permission === p2)
    }
  }

}
