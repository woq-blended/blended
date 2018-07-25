package blended.security.login.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.security.internal.SecurityActivator
import blended.security.login.TokenStore
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.Await

class TokenStoreSpec extends FreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  def withTokenStore[T](f : TokenStore => T) = {
    withSimpleBlendedContainer(baseDir) { sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.security" -> Some(() => new SecurityActivator()),
        "blended.security.login" -> Some(() => new LoginActivator())
      )) { sr =>
        val ref = sr.getServiceReference(classOf[TokenStore].getName())
        val store = sr.getService(ref).asInstanceOf[TokenStore]
        f(store)
      }
    }
  }

  "The token store" - {

    "should start empty" in {

      withTokenStore { store =>
        Await.result(store.listTokens(), 3.seconds) should be(empty)
      }
    }

    "should allow to create a new token" in {

      withTokenStore { store =>
        val token = Await.result(store.newToken("andreas", "mysecret".toCharArray(), None), 3.seconds).get

        token.id should be("andreas")
        token.expiresAt should be (0)

        val clientClaims = store.verifyToken(token.token)

        clientClaims.getHeader.getAlgorithm() should be ("RS512")
        clientClaims.getBody.getSubject() should be ("andreas")
        clientClaims.getBody.get("permissions", classOf[String]) should be ("admins,blended")

        Await.result(store.listTokens(), 3.seconds).size should be(1)
      }
    }

    "should allow to get and delete an existing token" in {

      withTokenStore { store =>
        val token = Await.result(store.newToken("andreas", "mysecret".toCharArray(), None), 3.seconds).get

        token.id should be("andreas")
        token.expiresAt should be (0)

        val token2 = Await.result(store.getToken("andreas"), 3.seconds).get

        assert(token === token2)
        val clientClaims = store.verifyToken(token2.token)

        clientClaims.getHeader.getAlgorithm() should be ("RS512")
        clientClaims.getBody.getSubject() should be ("andreas")
        clientClaims.getBody.get("permissions", classOf[String]) should be ("admins,blended")

        val token3 = Await.result(store.removeToken("andreas"), 3.seconds).get
        assert(token === token3)

        Await.result(store.listTokens(), 3.seconds) should be(empty)
      }
    }
  }
}
