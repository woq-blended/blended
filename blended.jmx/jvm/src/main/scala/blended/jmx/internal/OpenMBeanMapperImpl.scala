package blended.jmx.impl

import java.util.Date
import java.{lang => jl}
import java.{math => jm}
import scala.collection.JavaConverters._
import blended.jmx.OpenMBeanMapper

import javax.management.{DynamicMBean, MBeanNotificationInfo, ObjectName}
import javax.management.openmbean.{ArrayType, CompositeData, CompositeDataSupport, CompositeType, OpenMBeanAttributeInfo, OpenMBeanAttributeInfoSupport, OpenMBeanConstructorInfo, OpenMBeanInfoSupport, OpenMBeanOperationInfo, OpenType, SimpleType, TabularDataSupport, TabularType}

class OpenMBeanMapperImpl() extends OpenMBeanMapper {
  import OpenMBeanMapperImpl._

  def mapProduct(cc: Product): DynamicMBean = {
    val elements = productToMap(cc)

    println(s"Case class: ${cc} ==> ${elements}")

    val openAttributes: Array[OpenMBeanAttributeInfo] = elements.map { e =>
      new OpenMBeanAttributeInfoSupport(e._1, e._1, e._2._2, true, false, false)
    }.toArray[OpenMBeanAttributeInfo]
    val openConstructors: Array[OpenMBeanConstructorInfo] = Array()
    val openOperations: Array[OpenMBeanOperationInfo] = Array()
    val notifications: Array[MBeanNotificationInfo] = Array()

    val mBeanInfo = new OpenMBeanInfoSupport(
      cc.getClass().getName(),
      s"Generic MBean for class ${cc.getClass().getName()}",
      openAttributes,
      openConstructors,
      openOperations,
      notifications
    )

    val mbean = new GenericImmutableOpenMBean(mBeanInfo, elements)
    mbean
  }

  def productToMap(cc: Product): Map[String, Element] = {
    val values: Iterator[Any] = cc.productIterator
    cc.getClass.getDeclaredFields.filter { f =>
      f.getName != "$outer"
    }.map { f =>
      println(s"processing field of [${cc}]: ${f}")
      val value = values.next()
      println(s"    value of field [${f}]: [${value}]")
      val element = fieldToElement(f.getName, value)
      f.getName -> element
    }.toMap
  }

  def fieldToElement(name: String, field: Any): Element = {

    object SeqMatcher {
      def unapply(arg: Any): Option[Iterable[_]] = arg match {
        case x: Seq[_] => Some(x)
        case x: Set[_] => Some(x)
        case x: Array[_] => Some(x.toSeq)
        case _ => None
      }
    }

    field match {
      //        case x if x != null && x.getClass().isPrimitive() =>

      case x: Unit => (null: Void) -> SimpleType.VOID
      case x: Void => (null: Void) -> SimpleType.VOID

      case x: Boolean => Boolean.box(x) -> SimpleType.BOOLEAN
      case x: Char => Char.box(x) -> SimpleType.CHARACTER
      case x: Byte => Byte.box(x) -> SimpleType.BYTE
      case x: Short => Short.box(x) -> SimpleType.SHORT
      case x: Int => Int.box(x) -> SimpleType.INTEGER
      case x: Long => Long.box(x) -> SimpleType.LONG
      case x: Float => Float.box(x) -> SimpleType.FLOAT
      case x: Double => Double.box(x) -> SimpleType.DOUBLE

      case x: jl.Boolean => x -> SimpleType.BOOLEAN
      case x: jl.Character => x -> SimpleType.CHARACTER
      case x: jl.Byte => x -> SimpleType.BYTE
      case x: jl.Short => x -> SimpleType.SHORT
      case x: jl.Integer => x -> SimpleType.INTEGER
      case x: jl.Long => x -> SimpleType.LONG
      case x: jl.Float => x -> SimpleType.FLOAT
      case x: jl.Double => x -> SimpleType.DOUBLE
      case x: String => x -> SimpleType.STRING
      case x: BigDecimal => x.bigDecimal -> SimpleType.BIGDECIMAL
      case x: jm.BigDecimal => x -> SimpleType.BIGDECIMAL
      case x: BigInt => x.bigInteger -> SimpleType.BIGINTEGER
      case x: jm.BigInteger => x -> SimpleType.BIGINTEGER
      case x: Date => x -> SimpleType.DATE
      case x: ObjectName => x -> SimpleType.OBJECTNAME

      case x: Array[Boolean] => x -> new ArrayType(SimpleType.BOOLEAN, true)
      case x: Array[Char] => x -> new ArrayType(SimpleType.CHARACTER, true)
      case x: Array[Byte] => x -> new ArrayType(SimpleType.BYTE, true)
      case x: Array[Short] => x -> new ArrayType(SimpleType.SHORT, true)
      case x: Array[Int] => x -> new ArrayType(SimpleType.INTEGER, true)
      case x: Array[Long] => x -> new ArrayType(SimpleType.LONG, true)
      case x: Array[Float] => x -> new ArrayType(SimpleType.FLOAT, true)
      case x: Array[Double] => x -> new ArrayType(SimpleType.DOUBLE, true)

      case x: Array[jl.Boolean] => x -> new ArrayType(1, SimpleType.BOOLEAN)
      case x: Array[jl.Character] => x -> new ArrayType(1, SimpleType.CHARACTER)
      case x: Array[jl.Byte] => x -> new ArrayType(1, SimpleType.BYTE)
      case x: Array[jl.Short] => x -> new ArrayType(1, SimpleType.SHORT)
      case x: Array[jl.Integer] => x -> new ArrayType(1, SimpleType.INTEGER)
      case x: Array[jl.Long] => x -> new ArrayType(1, SimpleType.LONG)
      case x: Array[jl.Float] => x -> new ArrayType(1, SimpleType.FLOAT)
      case x: Array[jl.Double] => x -> new ArrayType(1, SimpleType.DOUBLE)
      case x: Array[String] => x -> new ArrayType(1, SimpleType.STRING)
      case x: Array[BigDecimal] => x -> new ArrayType(1, SimpleType.BIGDECIMAL)
      case x: Array[jm.BigDecimal] => x -> new ArrayType(1, SimpleType.BIGDECIMAL)
      case x: Array[BigInt] => x -> new ArrayType(1, SimpleType.BIGINTEGER)
      case x: Array[jm.BigInteger] => x -> new ArrayType(1, SimpleType.BIGINTEGER)
      case x: Array[Date] => x -> new ArrayType(1, SimpleType.DATE)
      case x: Array[ObjectName] => x -> new ArrayType(1, SimpleType.OBJECTNAME)

      // we are empty and readonly, so the element type doesn't matter
      case SeqMatcher(x) if x.isEmpty => Void.TYPE -> SimpleType.VOID

      // inspect first element
      case SeqMatcher(x) if !x.isEmpty =>
        val element: (Any, OpenType[_]) = fieldToElement(name, x.head)
        val rowType = new CompositeType(name, name, Array(name), Array(name), Array(element._2))
        val openType = new TabularType(name, name, rowType, Array(name))

        val value = new TabularDataSupport(openType)
        val allValues = x.map { e =>
          val data = fieldToElement(name, e)
          new CompositeDataSupport(rowType, Array(name), Array[AnyRef](data._1))
        }
        value.putAll(allValues.toArray[CompositeData])

        value -> openType

      // map case classes
      case x: Product =>
        val fields: Map[String, Element] = productToMap(x)
        val names = fields.map(_._1).toArray
        val types = fields.map(_._2._2).toArray

        val openType = new CompositeType(
          name,
          name,
          names,
          names,
          types
        )

        val value = new CompositeDataSupport(openType, fields.mapValues(_._1).asJava)

        value -> openType
    }

  }

}

object OpenMBeanMapperImpl {
  type Element = (AnyRef, OpenType[_])
}
