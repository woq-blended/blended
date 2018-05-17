package blended.security.akka.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.security.SubjectImplicits._
import blended.testsupport.security.DummyLoginConfiguration
import javax.security.auth.login.Configuration
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

class JAASSecurityDirectivesSpec
  extends FreeSpec
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  "An authenticated route" - {

    "with an security manager" - {

      val secDirectives = new JAASSecurityDirectives {}

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

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Configuration.setConfiguration(new DummyLoginConfiguration())
  }

}