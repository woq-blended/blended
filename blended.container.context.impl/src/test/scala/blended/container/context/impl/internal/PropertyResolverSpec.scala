package blended.container.context.impl.internal

import blended.container.context.api.{ContainerContext, ContainerIdentifierService, PropertyResolverException}
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import com.typesafe.config.Config
import org.scalatest.{FreeSpec, Matchers}

import scala.beans.BeanProperty
import scala.util.Try
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

    override def getContainerCryptoSupport(): ContainerCryptoSupport = BlendedCryptoSupport.initCryptoSupport("secret")
  }

  val idSvc : ContainerIdentifierService = new ContainerIdentifierService {

    @BeanProperty
    override lazy val uuid: String = "id"
    @BeanProperty
    override val containerContext: ContainerContext = ctCtxt
    @BeanProperty
    override val properties: Map[String, String] = Map(
      "foo" -> "bar",
      "bar" -> "test",
      "FOO" -> "BAR",
      "num" -> "12345",
      "version" -> "2.2.0",
      "typeA" -> "A",
      "typeB" -> "B"
    )

    def resolvePropertyString(value: String, additionalProps: Map[String, Any]) : Try[AnyRef] = Try {
      val r = ContainerPropertyResolver.resolve(this, value, additionalProps)
      r
    }
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
      intercept[PropertyResolverException] {
        ContainerPropertyResolver.resolve(idSvc, "$[[noprop]]")
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

      ContainerPropertyResolver.resolve(idSvc, "${{'$[[foo]]'.toUpperCase()}}") should be("BAR")
      ContainerPropertyResolver.resolve(idSvc, "${{'$[[FOO]]'.toLowerCase()}}") should be("bar")
      //ContainerPropertyResolver.resolve(idSvc, "${{#capitalize('$[[FOO]]'.toLowerCase())}}") should be("Bar")
      ContainerPropertyResolver.resolve(idSvc, "${{#left('$[[num]]', 4)}}") should be ("1234")
      ContainerPropertyResolver.resolve(idSvc, "${{#right('$[[num]]', 4)}}") should be ("2345")
      ContainerPropertyResolver.resolve(idSvc, "${{#right('$[[num]]', 6)}}") should be ("12345")
      ContainerPropertyResolver.resolve(idSvc, "${{'$[[version]]'.replaceAll('\\.', '_')}}") should be ("2_2_0")
//      ContainerPropertyResolver.resolve(idSvc, "${{#replace('$[[typeA]]')}}") should be ("1")
      ContainerPropertyResolver.resolve(idSvc, "$[[typeB(replace:A:1,replace:B:2))]]") should be ("2")

      val enc : String = idSvc.getContainerContext().getContainerCryptoSupport().encrypt("$[[foo]]$[[foo(upper)]]").get
      val line = "$[encrypted[" + enc + "]]"

      ContainerPropertyResolver.resolve(idSvc, line) should be ("barBAR")
    }

    "should allow to delay the property resolution" in {
      ContainerPropertyResolver.resolve(idSvc, line = "$[[foo(lower)]]$[delayed[${{#foo}}]]") should be ("bar${{#foo}}")
      ContainerPropertyResolver.resolve(idSvc, line = "$[delayed[${{#foo}}]]") should be ("${{#foo}}")
    }

    "should allow to resolve a spring expression" in {
      ContainerPropertyResolver.resolve(
        idSvc,
        line = "${{#foo}}",
        additionalProps = Map(
          "myProperty" -> "Blended"
        )
      ) should be ("")

      ContainerPropertyResolver.resolve(
        idSvc,
        line = "${{#myProperty}}",
        additionalProps = Map(
          "myProperty" -> "Blended"
        )
      ) should be ("Blended")
    }

    "should allow to nest lookups and expressions" in {
      ContainerPropertyResolver.resolve(
        idSvc,
        line = "$[[${{#myProperty}}]]",
        additionalProps = Map(
          "myProperty" -> "foo"
        )
      ) should be ("bar")
    }
  }
}
