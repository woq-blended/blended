package blended.jmx.json

import blended.jmx._
import blended.jmx.json.PrickleProtocol._
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.FreeSpec
import org.scalatest.matchers.should.Matchers
import prickle._

class JmxObjectJsonSpec extends FreeSpec
  with Matchers
  with ScalaCheckPropertyChecks {

  private val words : Seq[String] = Seq(
    "chemical", "meaty", "start", "atten", "tub", "four", "nut", "tread", "immolate", "straw", "toothpaste",
    "family", "highfalutin", "bee", "damp", "rustic", "savor", "hose", "open", "swot", "advertisement",
    "twist", "pest", "control", "concerned", "groovy", "tub", "death", "cabbage", "cow", "warn", "dive",
    "miniature", "awake", "inquisitive", "capture", "innate", "cabbage", "mixed", "breathe", "immolate",
    "tremendous", "crayon", "consign", "accessible", "inconclusive"
  )

  private def mapGen[T,U](g1 : Gen[T], g2 : Gen[U]) : Gen[Map[T,U]] = Gen.nonEmptyMap[T,U](
    for {
      k <- g1
      v <- g2
    } yield(k,v)
  )

  private val stringGen : Gen[String] = Gen.oneOf(words)

  private val stringMapGen : Gen[Map[String, String]] = mapGen[String, String](stringGen, stringGen)

  private val objNameGen : Gen[JmxObjectName] = for {
    domain <- stringGen
    props <- stringMapGen
  } yield JmxObjectName(domain, props)

  private val attributeGen : Gen[AttributeValue] = {

    def intGen : Gen[Int] = Gen.chooseNum[Int](Int.MinValue, Int.MaxValue)
    def bigIntGen : Gen[BigInt] = intGen.map(BigInt(_))
    def floatGen : Gen[Float] = Gen.chooseNum[Float](Float.MinValue, Float.MaxValue)
    def dblGen : Gen[Double] = Gen.chooseNum[Double](Double.MinValue, Double.MaxValue)
    def bigDecGen : Gen[BigDecimal] = dblGen.map(BigDecimal(_))
    def shortGen : Gen[Short] = Gen.chooseNum[Short](Short.MinValue, Short.MaxValue)
    def longGen : Gen[Long] = Gen.chooseNum[Long](Long.MinValue, Long.MaxValue)
    def byteGen : Gen[Byte] = Gen.chooseNum[Byte](Byte.MinValue, Byte.MaxValue)

    def unitAttr : Gen[AttributeValue] = Gen.const(UnitAttributeValue())
    def intAttr : Gen[AttributeValue] = intGen.map(IntAttributeValue)
    def shortAttr : Gen[AttributeValue] = shortGen.map(ShortAttributeValue)
    def longAttr : Gen[AttributeValue] = longGen.map(LongAttributeValue)
    def byteAttr : Gen[AttributeValue] = byteGen.map(ByteAttributeValue)
    def floatAttr : Gen[AttributeValue] = floatGen.map(FloatAttributeValue)
    def dblAttr : Gen[AttributeValue] = dblGen.map(DoubleAttributeValue)
    def bigIntAttr : Gen[AttributeValue] = bigIntGen.map(BigIntegerAtrributeValue)
    def bigDecAttr : Gen[AttributeValue] = bigDecGen.map(BigDecimalAtrributeValue)
    def stringAttr : Gen[AttributeValue] = stringGen.map(StringAttributeValue)
    def booleanAttr : Gen[AttributeValue] = Gen.oneOf(Gen.const(true), Gen.const(false)).map(BooleanAttributeValue)
    def objNameAttr : Gen[AttributeValue] = objNameGen.map(ObjectNameAttributeValue)

    def simpleAttr : Gen[AttributeValue] = Gen.oneOf(
      unitAttr, intAttr, shortAttr, longAttr, byteAttr, floatAttr, dblAttr,
      bigIntAttr, bigDecAttr, stringAttr, booleanAttr, objNameAttr
    )

    // We have to reduce the list size here, so the list eventually converges
    // see "Scalacheck in Action, pg 92ff.
    def arrAttr : Gen[AttributeValue] = Gen.sized { sz : Int =>
      for {
        cols <- Gen.choose(1, Math.max(2, sz + 1))
        a <- Gen.listOfN(cols, simpleAttr)
      } yield ListAttributeValue(a)
    }

    Gen.oneOf(simpleAttr, arrAttr)
  }

  private val beanInfoGen : Gen[JmxBeanInfo] = for {
    objName <- objNameGen
    attr <- mapGen[String, AttributeValue](stringGen, attributeGen)
  } yield JmxBeanInfo(objName, CompositeAttributeValue(attr))

  "The JmxObjectName should" - {

    "(de)serialize to / from JSON correctly" in {

      forAll(objNameGen) { objName =>
        val json: String = Pickle.intoString(objName)
        val obj2: JmxObjectName = Unpickle[JmxObjectName].fromString(json).get

        assert(objName.equals(obj2))
      }
    }
  }

  "The BeanInfo should" - {

    "(de)serialize to / from JSON correctly" in {
      forAll(beanInfoGen) { info =>
        val json : String = Pickle.intoString(info)
        val obj2 : JmxBeanInfo = Unpickle[JmxBeanInfo].fromString(json).get
        assert(info === obj2)
      }
    }
  }
}
