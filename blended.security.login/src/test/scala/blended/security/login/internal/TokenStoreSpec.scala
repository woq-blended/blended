package blended.security.login.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.security.BlendedPermissions
import blended.security.internal.SecurityActivator
import blended.security.login.TokenStore
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class TokenStoreSpec extends FreeSpec
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  def withTokenStore[T](f : TokenStore => T): T = {
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

        val clientClaims = store.verifyToken(token.webToken)

        clientClaims.getHeader.getAlgorithm() should be ("RS512")
        clientClaims.getBody.getSubject() should be ("andreas")

        val json : String = clientClaims.getBody().get("permissions", classOf[String])
        val permissions : BlendedPermissions = BlendedPermissions.fromJson(json).get

        permissions.granted.size should be (2)
        permissions.granted.find(_.permissionClass == Some("admins")) should be (defined)
        permissions.granted.find(_.permissionClass == Some("blended")) should be (defined)

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
        val clientClaims = store.verifyToken(token2.webToken)

        clientClaims.getHeader.getAlgorithm() should be ("RS512")
        clientClaims.getBody.getSubject() should be ("andreas")

        val json : String = clientClaims.getBody().get("permissions", classOf[String])
        val permissions : BlendedPermissions = BlendedPermissions.fromJson(json).get

        permissions.granted.size should be (2)
        permissions.granted.find(_.permissionClass == Some("admins")) should be (defined)
        permissions.granted.find(_.permissionClass == Some("blended")) should be (defined)

        val token3 = Await.result(store.removeToken("andreas"), 3.seconds).get
        assert(token === token3)

        Await.result(store.listTokens(), 3.seconds) should be(empty)
      }
    }
  }
}
