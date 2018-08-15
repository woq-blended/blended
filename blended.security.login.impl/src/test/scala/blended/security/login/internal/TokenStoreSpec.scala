package blended.security.login.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.security.PasswordCallbackHandler
import blended.security.internal.SecurityActivator
import blended.security.login.api.TokenStore
import blended.security.login.impl.LoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import javax.security.auth.Subject
import javax.security.auth.login.LoginContext
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

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

  def login(user: String, password : String) : Try[Subject] =  Try {
    val lc = new LoginContext("Test", new PasswordCallbackHandler(user, password.toCharArray()))
    lc.login()
    lc.getSubject()
  }

  "The token store" - {

    "should start empty" in {

      withTokenStore { store =>
        Await.result(store.listTokens(), 3.seconds) should be(empty)
      }
    }

    "should allow to create a new token" in {

      withTokenStore { store =>
        val subj = login("andreas", "mysecret").get
        val token = Await.result(store.newToken(subj, None), 3.seconds).get

        token.id should be("andreas")
        token.expiresAt should be (0)

        val permissions = store.verifyToken(token.webToken).get

        permissions.granted.size should be (2)
        permissions.granted.find(_.permissionClass == Some("admins")) should be (defined)
        permissions.granted.find(_.permissionClass == Some("blended")) should be (defined)

        Await.result(store.listTokens(), 3.seconds).size should be(1)
      }
    }

    "should allow to get and delete an existing token" in {

      withTokenStore { store =>
        val subj = login("andreas", "mysecret").get
        val token = Await.result(store.newToken(subj, None), 3.seconds).get

        token.id should be("andreas")
        token.expiresAt should be (0)

        val token2 = Await.result(store.getToken("andreas"), 3.seconds).get

        assert(token === token2)
        val permissions  = store.verifyToken(token2.webToken).get

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
