package blended.security.crypto

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class BlendedCryptoSupportSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  private val log : Logger = Logger[BlendedCryptoSupport]

  private val secret : String = "secret"
  private val cs : BlendedCryptoSupport = new BlendedCryptoSupport(secret, "AES")

  "The Crypto Support should" - {

    "encrypt and decrypt a given String" in {

      forAll { s : String =>
        whenever(s.nonEmpty) {
          val encrypted = cs.encrypt(s).get

          log.info(s"Encrypted [$s] to [$encrypted]")

          encrypted should not be s
          cs.decrypt(encrypted).get should be(s)
        }
      }
    }
  }
}
