package blended.container.context.impl.internal

import scala.util.control.NonFatal

import blended.container.context.api.{ContainerContext, PropertyResolverException}
import blended.testsupport.BlendedTestSupport
import blended.updater.config.Profile
import blended.util.RichTry._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PropertyResolverSpec extends AnyFreeSpec
  with Matchers {

  System.setProperty("COUNTRY", "cc")
  System.setProperty(Profile.Properties.PROFILE_PROPERTY_KEYS, "foo,bar,FOO,num,version,typeA,typeB,blended.country,blended.demoProp")
  System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
  System.setProperty("blended.container.home", BlendedTestSupport.projectTestOutput)
  val ctCtxt : ContainerContext = new ContainerContextImpl()

  System.setProperty("sysProp", "test")

  "The property resolver" - {

    "should yield the input string if no replacements are specified" in {
      ctCtxt.resolveString("foo").unwrap should be("foo")
    }

    "should throw an Exception when the end delimiter is missing" in {
      try {
        ctCtxt.resolveString("$[[foo").unwrap
        fail()
      } catch {
        case _ : PropertyResolverException =>
        case NonFatal(e)                   => fail(e)
      }
    }

    "should throw an exception when the property can't be resolved" in {
      intercept[PropertyResolverException] {
        ctCtxt.resolveString("$[[noprop]]").unwrap
      }
    }

    "should replace a single value in the replacement String" in {
      ctCtxt.resolveString("$[[foo]]").unwrap should be("bar")
      ctCtxt.resolveString("$[[foo]]$[[foo]]").unwrap should be("barbar")
      ctCtxt.resolveString("test$[[foo]]").unwrap should be("testbar")
    }

    "should replace a nested value in the replacement String" in {
      ctCtxt.resolveString("$[[$[[foo]]]]").unwrap should be("test")
    }

    "should fallback to sysytem properties if replacement property is not in container properties" in {
      ctCtxt.resolveString("$[[sysProp]]").unwrap should be("test")
    }

    "should apply the parameters if given in the replacement rule" in {
      ctCtxt.resolveString("$[[foo(upper)]]").unwrap should be("BAR")
      ctCtxt.resolveString("$[[FOO(lower)]]").unwrap should be("bar")
      ctCtxt.resolveString("$[[FOO(lower,capitalize)]]").unwrap should be("Bar")
      ctCtxt.resolveString("$[[num(left:4)]]").unwrap should be("1234")
      ctCtxt.resolveString("$[[num(right:4)]]").unwrap should be("2345")
      ctCtxt.resolveString("$[[num(right:6)]]").unwrap should be("12345")
      ctCtxt.resolveString("$[[version(replace:\\.:_)]]").unwrap should be("2_2_0")
      ctCtxt.resolveString("$[[typeA(replace:A:1)(replace:B:2))]]").unwrap should be("1")
      ctCtxt.resolveString("$[[typeB(replace:A:1,replace:B:2))]]").unwrap should be("2")

      ctCtxt.resolveString(s"$${{'$$[[foo]]'.toUpperCase()}}").unwrap should be("BAR")
      ctCtxt.resolveString(s"$${{'$$[[FOO]]'.toLowerCase()}}").unwrap should be("bar")
      ctCtxt.resolveString(s"$$[[foo(capitalize)]]").unwrap should be("Bar")
      ctCtxt.resolveString(s"$${{#left('$$[[num]]', 4)}}").unwrap should be("1234")
      ctCtxt.resolveString(s"$${{#right('$$[[num]]', 4)}}").unwrap should be("2345")
      ctCtxt.resolveString(s"$${{#right('$$[[num]]', 6)}}").unwrap should be("12345")
      ctCtxt.resolveString(s"$${{'$$[[version]]'.replaceAll('\\.', '_')}}").unwrap should be("2_2_0")
      //      ctCtxt.resolveString("${{#replace('$[[typeA]]')}}") should be ("1")
      ctCtxt.resolveString("$[[typeB(replace:A:1,replace:B:2))]]").unwrap should be("2")

      val enc : String = ctCtxt.cryptoSupport.encrypt("$[[foo]]$[[foo(upper)]]").unwrap
      val line = "$[encrypted[" + enc + "]]"

      ctCtxt.resolveString(line).unwrap should be("barBAR")
    }

    "should allow to delay the property resolution" in {
      ctCtxt.resolveString(s"$$[[foo(lower)]]$$[delayed[$${{#foo}}]]").unwrap should be(s"bar$${{#foo}}")
      ctCtxt.resolveString(s"$$[delayed[$${{#foo}}]]").unwrap should be(s"$${{#foo}}")
    }

    "should allow to resolveString a spring expression" in {
      ctCtxt.resolveString(
        s"$${{#foo}}",
        additionalProps = Map(
          "myProperty" -> "Blended"
        )
      ).unwrap should be("")

      ctCtxt.resolveString(
        s"$${{#myProperty}}",
        additionalProps = Map(
          "myProperty" -> "Blended"
        )
      ).unwrap should be("Blended")
    }

    "should allow to nest lookups and expressions" in {
      ctCtxt.resolveString(
        s"$$[[$${{#myProperty}}]]",
        additionalProps = Map(
          "myProperty" -> "foo"
        )
      ).unwrap should be("bar")
    }
  }
}
