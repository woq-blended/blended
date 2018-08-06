package blended.security

import org.scalatest.FreeSpec

class BlendedPermissionSpec extends FreeSpec {

  "The BlendedPermission should" - {

    "match if only the permission class is set" in {

      val permission = BlendedPermission(permissionClass = "container")

      assert(permission.allows(BlendedPermission(permissionClass = "container")))
      assert(permission.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq("de")))))
      assert(permission.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq("bg")))))

      assert(!permission.allows(BlendedPermission(permissionClass = "foo")))
    }

    "match if all of the specified properties match the controlled object" in {

      val noCountries = Seq("ro")
      val countries = Seq("de", "bg", "cz")

      val p1 = BlendedPermission(permissionClass = "container", properties = Map("country" -> countries))

      assert(countries.forall { c =>
        p1.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq(c))))
      })

      assert(noCountries.forall{ c =>
        !p1.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq(c))))
      })

      val p2 = BlendedPermission(permissionClass = "container", properties = Map("country" -> countries, "location" -> Seq("09999")))
      assert(countries.forall { c =>
        !p2.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq(c))))
      })

      assert(countries.forall { c =>
        p2.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq(c), "location" -> Seq("09999"))))
      })
    }

    "match if the controlled object has properties not specified in the granting permission" in {
      val countries = Seq("de", "bg", "cz")
      val p1 = BlendedPermission(permissionClass = "container", properties = Map("country" -> countries))

      assert(countries.forall { c =>
        p1.allows(BlendedPermission(permissionClass = "container", properties = Map("country" -> Seq(c), "location" -> Seq("09999"))))
      })
    }
  }



}
