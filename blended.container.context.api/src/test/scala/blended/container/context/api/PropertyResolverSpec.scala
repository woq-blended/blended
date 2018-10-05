package blended.container.context.api

import com.typesafe.config.Config
import org.scalatest.{FreeSpec, Matchers}

import scala.util.control.NonFatal


//noinspection NotImplementedCode
class PropertyResolverSpec extends FreeSpec
  with Matchers {

  val ctCtxt : ContainerContext = new ContainerContext() {

    override def getProfileDirectory() : String  = ???

    override def getProfileConfigDirectory() : String = ???

    override def getContainerLogDirectory() : String = ???

    override def getContainerDirectory() : String = ???

    override def getContainerConfigDirectory() : String = ???

    override def getContainerHostname() : String = ???

    override def getContainerConfig() : Config = ???
  }

  val idSvc : ContainerIdentifierService = new ContainerIdentifierService {

    override lazy val uuid: String = "id"
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
        ContainerPropertyResolver.resolve(idSvc, "$[[foo")
        fail()
      } catch {
        case _ : PropertyResolverException =>
        case NonFatal(e) => fail(e)
      }
    }

    "should throw an exception when the property can't be resolved" in {
      try {
        ContainerPropertyResolver.resolve(idSvc, "$[[noprop]]")
        fail()
      } catch {
        case _ : PropertyResolverException =>
        case _ : Throwable => fail()
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

    "should apply the parameters if given in the replacement rule" in {
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
