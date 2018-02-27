package blended.container.context.internal

import blended.container.context.{ContainerContext, ContainerIdentifierService, ContainerPropertyResolver, PropertyResolverException}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, Matchers}

import scala.util.control.NonFatal


class PropertyResolverSpec extends FreeSpec
  with Matchers {

  val ctCtxt = new ContainerContext() {

    override def getProfileDirectory() = ???

    override def getProfileConfigDirectory() = ???

    override def getContainerLogDirectory() = ???

    override def getContainerDirectory() = ???

    override def getContainerConfigDirectory() = ???

    override def getContainerHostname() = ???

    override def getContainerConfig() = ???
  }

  val idSvc : ContainerIdentifierService = new ContainerIdentifierService {

    override val uuid: String = "id"
    override val containerContext: ContainerContext = ctCtxt
    override val properties: Map[String, String] = Map(
      "foo" -> "bar",
      "bar" -> "test",
      "FOO" -> "BAR",
      "num" -> "12345",
      "version" -> "2.2.0",
      "typeA" -> "A",
      "typeB" -> "B"
    )
  }

  System.getProperties().setProperty("sysProp", "test")

  "The property resolver" - {

    "should yield the input string if no replacements are specified" in {

      ContainerPropertyResolver.resolve(idSvc, "foo") should be ("foo")
    }

    "should throw an Exception when the end delimiter is missing" in {

      try {
        val s = ContainerPropertyResolver.resolve(idSvc, "$[[foo")
        fail()
      } catch {
        case pre : PropertyResolverException =>
        case NonFatal(e) => fail()
      }
    }

    "should throw an exception when the property can't be resolved" in {
      try {
        ContainerPropertyResolver.resolve(idSvc, "$[[noprop]]")
        fail()
      } catch {
        case pre : PropertyResolverException =>
        case _ => fail()
      }
    }

    "should replace a single value in the replacement String" in {

      ContainerPropertyResolver.resolve(idSvc, "$[[foo]]") should be("bar")
      ContainerPropertyResolver.resolve(idSvc, "$[[foo]]$[[foo]]") should be ("barbar")
      ContainerPropertyResolver.resolve(idSvc, "test$[[foo]]") should be ("testbar")

    }

    "should replace a nested value in the replacement String" in {
      ContainerPropertyResolver.resolve(idSvc, "$[[$[[foo]]]]") should be("test")
    }

    "should fallback to sysytem properties if replacement property is not in container properties" in {
      ContainerPropertyResolver.resolve(idSvc, "$[[sysProp]]") should be("test")
    }

    "should apply the paramters if given in the replacement rule" in {
      ContainerPropertyResolver.resolve(idSvc, "$[[foo(upper)]]") should be("BAR")
      ContainerPropertyResolver.resolve(idSvc, "$[[FOO(lower)]]") should be("bar")
      ContainerPropertyResolver.resolve(idSvc, "$[[FOO(lower,capitalize)]]") should be("Bar")
      ContainerPropertyResolver.resolve(idSvc, "$[[num(left:4)]]") should be ("1234")
      ContainerPropertyResolver.resolve(idSvc, "$[[num(right:4)]]") should be ("2345")
      ContainerPropertyResolver.resolve(idSvc, "$[[num(right:6)]]") should be ("12345")
      ContainerPropertyResolver.resolve(idSvc, "$[[version(replace:\\.:_)]]") should be ("2_2_0")
      ContainerPropertyResolver.resolve(idSvc, "$[[typeA(replace:A:1)(replace:B:2))]]") should be ("1")
      ContainerPropertyResolver.resolve(idSvc, "$[[typeB(replace:A:1,replace:B:2))]]") should be ("2")
    }
  }

}
