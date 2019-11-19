package blended.jmx.internal

import java.util.Date
import java.{lang => jl, math => jm}

import blended.jmx.JmxAttributeCompanion
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import javax.management.ObjectName
import javax.management.openmbean.{ArrayType, SimpleType, TabularData}
import org.scalacheck.Arbitrary
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

class OpenMBeanMapperSpec extends LoggingFreeSpec with PropertyChecks with Matchers {

  import OpenMBeanMapperSpec._
  import blended.jmx.internal.TestData._

  val log = Logger[this.type]

  "The OpenMBeanMapperImpl should" - {

    val mapper = new OpenMBeanMapperImpl()

    "map standard Java types and primitives" - {
      def testMapping[T: ClassTag](`type`: SimpleType[_], box: T => _ = null, testUnboxed: Boolean = true)(implicit arb: Arbitrary[T]): Unit = {
        val rcClass = classTag[T].runtimeClass
        s"of type ${`type`} (classTag: ${rcClass.getName()})" in {
          forAll { d: T =>
            if (testUnboxed) {
              assert(mapper.fieldToElement("prim", d) === (d -> `type`))
            }
            Option(box).foreach { b =>
              val boxed = b(d)
              assert(mapper.fieldToElement("boxed", boxed) === (boxed -> `type`))
            }
          }
        }
      }

      testMapping(SimpleType.BOOLEAN, Boolean.box)
      testMapping(SimpleType.BYTE, Byte.box)
      testMapping(SimpleType.SHORT, Short.box)
      testMapping(SimpleType.INTEGER, Int.box)
      testMapping(SimpleType.LONG, Long.box)
      testMapping(SimpleType.FLOAT, Float.box)
      testMapping(SimpleType.DOUBLE, Double.box)
      testMapping[String](SimpleType.STRING)
      testMapping[BigDecimal](SimpleType.BIGDECIMAL, (x: BigDecimal) => x.bigDecimal, false)
      testMapping[BigInt](SimpleType.BIGINTEGER, (x: BigInt) => x.bigInteger, false)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
      testMapping[ObjectName](SimpleType.OBJECTNAME)
      testMapping[Date](SimpleType.DATE)
    }

    "map Java arrays" - {
      def testMapping[T: ClassTag: Arbitrary](type0: SimpleType[_]): Unit = {
        val rcClass = classTag[T].runtimeClass
        val isPrim = rcClass.isPrimitive()
        s"of ${if (isPrim) "privitive " else ""}type ${type0} (classTag: ${rcClass.getName()})" in {
          val expectedType = new ArrayType(type0, isPrim)
          forAll { d: Array[T] =>
            assert(mapper.fieldToElement("d", d) === (d -> expectedType))
          }
        }
      }

      testMapping[jl.Boolean](SimpleType.BOOLEAN)
      testMapping[jl.Byte](SimpleType.BYTE)
      testMapping[jl.Short](SimpleType.SHORT)
      testMapping[jl.Integer](SimpleType.INTEGER)
      testMapping[jl.Long](SimpleType.LONG)
      testMapping[jl.Float](SimpleType.FLOAT)
      testMapping[jl.Double](SimpleType.DOUBLE)

      testMapping[Boolean](SimpleType.BOOLEAN)
      testMapping[Byte](SimpleType.BYTE)
      testMapping[Short](SimpleType.SHORT)
      testMapping[Int](SimpleType.INTEGER)
      testMapping[Long](SimpleType.LONG)
      testMapping[Float](SimpleType.FLOAT)
      testMapping[Double](SimpleType.DOUBLE)

      testMapping[String](SimpleType.STRING)
      testMapping[BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[BigInt](SimpleType.BIGINTEGER)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
      testMapping[Date](SimpleType.DATE)

    }

    "map Scala seqs" - {
      def testMapping[T: ClassTag: Arbitrary](type0: SimpleType[_]): Unit = {
        val rcClass = classTag[T].runtimeClass
        val isPrim = rcClass.isPrimitive()
        s"of ${if (isPrim) "primitive " else ""}type ${type0} (classTag: ${rcClass.getName()})" in {
          //          val expectedType = new ArrayType( type0, isPrim)
          forAll { d: Seq[T] =>
            val (value, mappedType) = mapper.fieldToElement("d", d)
            if (d.isEmpty) {
              assert(mappedType === SimpleType.VOID)
              assert(value === null)
            } else {
              assert(value.isInstanceOf[TabularData])
              assert(value.asInstanceOf[TabularData].getTabularType().getIndexNames().asScala === List("index"))
              assert(value.asInstanceOf[TabularData].size() === d.size)
            }
          }
        }
      }

      testMapping[jl.Boolean](SimpleType.BOOLEAN)
      testMapping[jl.Byte](SimpleType.BYTE)
      testMapping[jl.Short](SimpleType.SHORT)
      testMapping[jl.Integer](SimpleType.INTEGER)
      testMapping[jl.Long](SimpleType.LONG)
      testMapping[jl.Float](SimpleType.FLOAT)
      testMapping[jl.Double](SimpleType.DOUBLE)
      testMapping[Date](SimpleType.DATE)
      testMapping[ObjectName](SimpleType.OBJECTNAME)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
    }

    "map Scala maps" - {
      def testMapping[T: ClassTag: Arbitrary](type0: SimpleType[_]): Unit = {
        val rcClass = classTag[T].runtimeClass
        val isPrim = rcClass.isPrimitive()
        s"of ${if (isPrim) "primitive " else ""}type ${type0} (classTag: ${rcClass.getName()})" in {
          //          val expectedType = new ArrayType( type0, isPrim)
          forAll { d: Map[T, T] =>
            val (value, mappedType) = mapper.fieldToElement("d", d)
            if (d.isEmpty) {
              assert(mappedType === SimpleType.VOID)
              assert(value === null)
            } else {
              assert(value.isInstanceOf[TabularData])
              assert(value.asInstanceOf[TabularData].getTabularType().getIndexNames().asScala === List("index"))
              assert(value.asInstanceOf[TabularData].size() === d.size)
            }
          }
        }
      }

      testMapping[jl.Boolean](SimpleType.BOOLEAN)
      testMapping[jl.Byte](SimpleType.BYTE)
      testMapping[jl.Short](SimpleType.SHORT)
      testMapping[jl.Integer](SimpleType.INTEGER)
      testMapping[jl.Long](SimpleType.LONG)
      testMapping[jl.Float](SimpleType.FLOAT)
      testMapping[jl.Double](SimpleType.DOUBLE)
      testMapping[Date](SimpleType.DATE)
      testMapping[ObjectName](SimpleType.OBJECTNAME)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
    }

    "map Java collections" - {
      def testMapping[T: ClassTag: Arbitrary](type0: SimpleType[_]): Unit = {
        val rcClass = classTag[T].runtimeClass
        val isPrim = rcClass.isPrimitive()
        s"of ${if (isPrim) "primitive " else ""}type ${type0} (classTag: ${rcClass.getName()})" in {
          //          val expectedType = new ArrayType( type0, isPrim)
          forAll { d: List[T] =>
            val col = d.asJava
            val (value, mappedType) = mapper.fieldToElement("d", col)
            if (col.isEmpty) {
              assert(mappedType === SimpleType.VOID)
              assert(value === null)
            } else {
              assert(value.isInstanceOf[TabularData])
              assert(value.asInstanceOf[TabularData].getTabularType().getIndexNames().asScala === List("index"))
              assert(value.asInstanceOf[TabularData].size() === col.size)
            }
          }
        }
      }

      testMapping[jl.Boolean](SimpleType.BOOLEAN)
      testMapping[jl.Byte](SimpleType.BYTE)
      testMapping[jl.Short](SimpleType.SHORT)
      testMapping[jl.Integer](SimpleType.INTEGER)
      testMapping[jl.Long](SimpleType.LONG)
      testMapping[jl.Float](SimpleType.FLOAT)
      testMapping[jl.Double](SimpleType.DOUBLE)
      testMapping[Date](SimpleType.DATE)
      testMapping[ObjectName](SimpleType.OBJECTNAME)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
    }

    "map Java maps" - {
      def testMapping[T: ClassTag: Arbitrary](type0: SimpleType[_]): Unit = {
        val rcClass = classTag[T].runtimeClass
        val isPrim = rcClass.isPrimitive()
        s"of ${if (isPrim) "primitive " else ""}type ${type0} (classTag: ${rcClass.getName()})" in {
          //          val expectedType = new ArrayType( type0, isPrim)
          forAll { d: Map[T, T] =>
            val col = d.asJava
            val (value, mappedType) = mapper.fieldToElement("d", col)
            if (col.isEmpty) {
              assert(mappedType === SimpleType.VOID)
              assert(value === null)
            } else {
              assert(value.isInstanceOf[TabularData])
              assert(value.asInstanceOf[TabularData].getTabularType().getIndexNames().asScala === List("index"))
              assert(value.asInstanceOf[TabularData].size() === col.size)
            }
          }
        }
      }

      testMapping[jl.Boolean](SimpleType.BOOLEAN)
      testMapping[jl.Byte](SimpleType.BYTE)
      testMapping[jl.Short](SimpleType.SHORT)
      testMapping[jl.Integer](SimpleType.INTEGER)
      testMapping[jl.Long](SimpleType.LONG)
      testMapping[jl.Float](SimpleType.FLOAT)
      testMapping[jl.Double](SimpleType.DOUBLE)
      testMapping[Date](SimpleType.DATE)
      testMapping[ObjectName](SimpleType.OBJECTNAME)
      testMapping[jm.BigDecimal](SimpleType.BIGDECIMAL)
      testMapping[jm.BigInteger](SimpleType.BIGINTEGER)
    }

    "test with blended.jmx" in {
      val unmapper = JmxAttributeCompanion
      val mapped = mapper.mapProduct(caseClass1)
      mapped.getMBeanInfo.getAttributes.map { ai =>
        log.info(ai.getName + " => " + unmapper.lift(mapped.getAttribute(ai.getName)))
      }

    }
  }
}

object OpenMBeanMapperSpec {

  case class CaseClass1(
    aString: String,
    aInt: Int,
    aStringArray: Array[String],
    aStringSeq: Seq[String],
    aStringMap: Map[String, String]

  )

  val caseClass1 = CaseClass1(
    aString = "aString",
    aInt = 1,
    aStringArray = Array("s"),
    aStringSeq = Seq("s"),
    aStringMap = Map("1" -> "One", "2" -> "Two")
  )

  case class CaseClass2(
    name: String,
    cc1: CaseClass1
  )

}
