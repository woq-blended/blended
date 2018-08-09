package blended.security

import org.scalatest.{FreeSpec, Matchers}

class BlendedPermissionSpec extends FreeSpec
  with Matchers {

  private val noCountries = Seq("ro")
  private val countries = Seq("de", "bg", "cz")

  "The BlendedPermission should" - {

    "not match if the permission class is empty" in {

      val permission = BlendedPermission(None)

      assert(!permission.allows(BlendedPermission(permissionClass = Some("container"))))
      assert(!permission.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq("de")))))
      assert(!permission.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq("bg")))))
      assert(!permission.allows(BlendedPermission(permissionClass = Some("foo"))))
    }

    "match if only the permission class is set" in {

      val permission = BlendedPermission(permissionClass = Some("container"))

      assert(permission.allows(BlendedPermission(permissionClass = Some("container"))))
      assert(permission.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq("de")))))
      assert(permission.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq("bg")))))

      assert(!permission.allows(BlendedPermission(permissionClass = Some("foo"))))
    }

    "match if all of the specified properties match the controlled object" in {



      val p1 = BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> countries))

      assert(countries.forall { c =>
        p1.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq(c))))
      })

      assert(noCountries.forall{ c =>
        !p1.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq(c))))
      })

      val p2 = BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> countries, "location" -> Seq("09999")))
      assert(countries.forall { c =>
        !p2.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq(c))))
      })

      assert(countries.forall { c =>
        p2.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq(c), "location" -> Seq("09999"))))
      })
    }

    "match if the controlled object has properties not specified in the granting permission" in {
      val countries = Seq("de", "bg", "cz")
      val p1 = BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> countries))

      assert(countries.forall { c =>
        p1.allows(BlendedPermission(permissionClass = Some("container"), properties = Map("country" -> Seq(c), "location" -> Seq("09999"))))
      })
    }

    "a merge with a permission having an empty class should have an empty permission class " in {

      val p1 = BlendedPermission(permissionClass = Some("test"))
      val p2 = BlendedPermission(permissionClass = None)

      p1.merge(p2).permissionClass should be (empty)
      p2.merge(p1).permissionClass should be (empty)
    }

    "a merge with unmatched permissionClasses should have an empty permission class" in {

      val p1 = BlendedPermission(permissionClass = Some("foo"))
      val p2 = BlendedPermission(permissionClass = Some("bar"))

      p1.merge(p2).permissionClass should be (empty)
      p2.merge(p1).permissionClass should be (empty)
    }

    "a merge of 2 permission with a property only set in one should not restrict on that property" in {
      val p1 = BlendedPermission(permissionClass = Some("foo"), properties = Map("country" -> countries))
      val p2 = BlendedPermission(permissionClass = Some("bar"))

      p1.merge(p2).properties.get("country") should be (empty)
      p2.merge(p1).properties.get("country") should be (empty)
    }

    "a merge of 2 permissions with restrictions on the same property should combine the restrictions" in {
      val p1 = BlendedPermission(permissionClass = Some("foo"), properties = Map("country" -> Seq("de", "bg")))
      val p2 = BlendedPermission(permissionClass = Some("foo"), properties = Map("country" -> Seq("de", "cz")))

      val values = p1.merge(p2).properties.getOrElse("country", Seq.empty)
      values.size should be (3)
      values should contain only ("de", "bg", "cz")
    }
  }
}
