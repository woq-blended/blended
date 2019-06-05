package blended.security.ssl.internal

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class PasswordHasherSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  "The password hasher should" - {

    "yield the same string if used with the same salt and pwd" in {

      val hasher : PasswordHasher = new PasswordHasher("salt")

      forAll { pwd : String =>
        whenever(pwd.nonEmpty) {
          val pwd1 : String = hasher.password(pwd)
          val pwd2 : String = hasher.password(pwd)

          pwd1 should equal(pwd2)
        }
      }
    }

    "yield a different string for different passwords" in {
      val hasher = new PasswordHasher("salt")

      forAll { (p1 : String, p2 : String) =>
        whenever(p1.nonEmpty && p2.nonEmpty && p1 != p2) {
          val pwd1 : String = hasher.password(p1)
          val pwd2 : String = hasher.password(p2)

          pwd1 should not equal pwd2
        }
      }
    }

    "yield a different string for different salts and the same password" in {

      forAll { (s1 : String, s2 : String) =>
        whenever(s1.nonEmpty && s2.nonEmpty && s1 != s2) {
          val h1 = new PasswordHasher(s1)
          val h2 = new PasswordHasher(s2)

          forAll { p : String =>
            val pwd1 : String = h1.password(p)
            val pwd2 : String = h2.password(p)
            pwd1 should not equal pwd2
          }
        }
      }
    }
  }
}
