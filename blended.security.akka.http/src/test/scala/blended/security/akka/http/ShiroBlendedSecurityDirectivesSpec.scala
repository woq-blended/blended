package blended.security.akka.http

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.FreeSpec
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.StatusCodes
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.subject.Subject
import org.apache.shiro.config.IniSecurityManagerFactory

class ShiroBlendedSecurityDirectivesSpec
  extends FreeSpec
  with ScalatestRouteTest {

  "An authenticated route" - {

    "without an security manager" - {
      val secDirectives = new ShiroBlendedSecurityDirectives(() => None)
      import secDirectives._

      val route = Route.seal {
        get {
          path("hello") {
            authenticated { user =>
              complete("User " + user)
            }
          }
        }
      }

      "should have unauthorized status when no credentials are given" in {
        Get("/hello") ~> route ~> check {
          assert(status === StatusCodes.Unauthorized)
        }
      }

      "should have unauthorized status when no securitymanager service is found" in {
        Get("/hello") ~> route ~> check {
          assert(status === StatusCodes.Unauthorized)
        }
      }

    }

    "with an security manager" - {
      val secMgr = new IniSecurityManagerFactory("classpath:test-shiro.ini").getInstance();
      assert(secMgr != null)

      val secDirectives = new ShiroBlendedSecurityDirectives(() => Some(secMgr))
      import secDirectives._

      val route = Route.seal {
        get {
          path("hello") {
            authenticated { user =>
              complete("User " + user.getPrincipal())
            }
          }
        }
      }

      val validCreds = BasicHttpCredentials("root", "mysecret")
      val invalidPasswordCreds = BasicHttpCredentials("admin", "pass")
      val incalidUserCreds = BasicHttpCredentials("user", "mysecret")
      val invalidCreds = BasicHttpCredentials("user", "pass")

      "should get authorized access when credentials are correct" in {
        Get("/hello") ~> addCredentials(validCreds) ~> route ~> check {
          assert(handled === true)
          assert(responseAs[String] === "User root")
        }
      }
      "should not get authorized access when user is incorrect" in {
        Get("/hello") ~> addCredentials(incalidUserCreds) ~> route ~> check {
          assert(handled === true)
          assert(status === StatusCodes.Unauthorized)
        }
      }
      "should not get authorized access when password is incorrect" in {
        Get("/hello") ~> addCredentials(invalidPasswordCreds) ~> route ~> check {
          assert(handled === true)
          assert(status === StatusCodes.Unauthorized)
        }
      }
      "should not get authorized access when user and password is incorrect" in {
        Get("/hello") ~> addCredentials(invalidCreds) ~> route ~> check {
          assert(handled === true)
          assert(status === StatusCodes.Unauthorized)
        }
      }

    }

  }

}