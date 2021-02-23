package blended.security.crypto

import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

class BlendedCryptoSupportSpec extends LoggingFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private val log: Logger = Logger[BlendedCryptoSupport]

  private val secret: String = "secret"
  private val cs: BlendedCryptoSupport = new BlendedCryptoSupport(secret, "AES")

  private val genStrings = Gen.asciiStr

  "The Crypto Support should" - {

    "encrypt and decrypt a given String" in {

      forAll(genStrings) { s: String =>
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
