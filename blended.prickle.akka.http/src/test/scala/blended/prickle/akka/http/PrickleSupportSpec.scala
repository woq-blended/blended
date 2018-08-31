package blended.prickle.akka.http

import scala.util.Success

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.util.logging.Logger
import microjson.JsValue
import org.scalatest.FreeSpec
import prickle.JsConfig
import prickle.PConfig
import prickle.Pickle
import prickle.Pickler
import prickle.Unpickle

class PrickleSupportSpec extends FreeSpec with ScalatestRouteTest with PrickleSupport {

  private[this] val log = Logger[PrickleSupportSpec]

  case class Foo(bar: String, baz: Long)

  implicit val prickleConfig: PConfig[JsValue] = JsConfig(areSharedObjectsSupported = false)
  implicit val fooPickler: Pickler[Foo] = Pickler.materializePickler[Foo]

  val testRoute = get {
    complete(Foo("Hi", 42L))
  } ~ post {
    entity(as[Foo]) {
      case Foo(bar, baz) => complete(s"Got a foo bar ${bar} with baz ${baz}")
    }
  }

  "pickling" in {
    val foo = Foo("Foo", 0L)
    assert(Unpickle[Foo].fromString(Pickle.intoString(foo)) === Success(foo))
  }

  "marshal" in {
    log.info("About to GET")
    Get() ~> testRoute ~> check {
      assert(contentType === PrickleSupport.prickleMediaType.toContentType)
      //      log.info(s"got: ${responseAs[String]}")
      assert(responseAs[Foo] === Foo("Hi", 42L))
      //      assert(entityAs[String] === Pickle.intoString(Foo("Hi", 42L)))
    }
  }

  "unmarshal" in {
    log.info(s"About to POST ${Foo("Hello", 1L)}")
    Post("/", HttpEntity(prickleMediaType.toContentType, Pickle.intoString(Foo("Hello", 1L)))) ~> testRoute ~> check {
      log.info(s"Got: ${responseAs[String]}")
      assert(responseAs[String] === "Got a foo bar Hello with baz 1")
    }
  }

}
