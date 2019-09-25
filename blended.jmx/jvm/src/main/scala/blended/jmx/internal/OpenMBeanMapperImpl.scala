package blended.jmx.internal

import java.util.Date
import java.{lang => jl}
import java.{util => ju}
import java.{math => jm}
import scala.collection.JavaConverters._
import blended.jmx.OpenMBeanMapper

import javax.management.{DynamicMBean, MBeanNotificationInfo, ObjectName}
import javax.management.openmbean._

class OpenMBeanMapperImpl() extends OpenMBeanMapper {
  import OpenMBeanMapperImpl._

  def mapProduct(cc: Product): DynamicMBean = {
    val elements = productToMap(cc)

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
    if(cc.productArity == 0) {
      // an object?
      // just store the name
      Map("object" -> fieldToElement("name", cc.getClass().getName()))
    }
    else {
      val values: Iterator[Any] = cc.productIterator
      cc.getClass().getDeclaredFields().filter { f =>
        f.getName != "$outer"
      }.map { f =>
        val value = values.next()
        val element = fieldToElement(f.getName, value)
        f.getName -> element
      }.toMap
    }
  }

  def fieldToElement(name: String, field: Any): Element = {

    object SeqMatcher {
      def unapply(arg: Any): Option[Iterable[_]] = arg match {
        case x: Seq[_] => Some(x)
        case x: Set[_] => Some(x)
        case x: Array[_] => Some(x.toSeq)
        case x: ju.Collection[_] => Some(x.asScala)
        case _ => None
      }
    }

    object MapMatcher {
      def unapply(arg: Any): Option[Map[_, _]] = arg match {
        case x: Map[_, _] => Some(x)
        case x: ju.Map[_, _] => Some(x.asScala.toMap)
        case _ => None
      }
    }

    field match {
      //        case x if x != null && x.getClass().isPrimitive() =>

      case null => (null: Void) -> SimpleType.VOID
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
      case SeqMatcher(x) if x.isEmpty => (null: Void) -> SimpleType.VOID
      case MapMatcher(x) if x.isEmpty => (null: Void) -> SimpleType.VOID

      // inspect first element
      case SeqMatcher(x) if !x.isEmpty =>
        // Note to myself: If I understand correctly, the tabulardata concept need to refer to some kind of "index"
        // in the same sence as an index in a SQL table, which must contain of at least one item name.
        // These index items must be items of the composite type and IIUC unique in the same tabular data.
        // Because of that that uniqueness constraint (which I inferred from the fact that putting two elements with the same data gives
        // me a KeyAlreadyExistsException) I add a generic index to the compositetype, which simply increments by one.

        val (_, elementType) = fieldToElement(name, x.head)
        val indexName = "index"
        val itemNames = Array(indexName, name)
        val rowType = new CompositeType(
          /*typename*/ s"SeqOf${elementType.getTypeName()}",
          /*description*/ name,
          /*itemNames*/ itemNames,
          /*itemDescrptions*/ Array("Row-Index", name),
          /*itemTypes*/Array(SimpleType.INTEGER, elementType))
        val tabularType = new TabularType(name, name, rowType, Array(indexName))

        val value = new TabularDataSupport(tabularType)
        x.zipWithIndex.foreach { case (e, index) =>
          val (eData, _) = fieldToElement(name, e)
          val itemValues = Array[AnyRef](Int.box(index), eData)
          val cData = new CompositeDataSupport(rowType, itemNames, itemValues)
          // println(s"Created a comp data support with compositeType=${rowType}, itemNames=${itemNames}, itemValues=${itemValues}")
          value.put(cData)
        }

        value -> tabularType

      case MapMatcher(x) =>
        //
        val (_, keyType) = fieldToElement("key", x.toSeq.head._1)
        val (_, valueType) = fieldToElement("value", x.toSeq.head._2)
        val indexName = "index"
        val itemNames = Array(indexName, "key", "value")
        val rowType = new CompositeType(
          /*typename*/ name,
          /*description*/ name,
          /*itemNames*/ itemNames,
          /*itemDescrptions*/ Array("Row-Index", "key", "value"),
          /*itemTypes*/Array(SimpleType.INTEGER, keyType, valueType))
        val tabularType = new TabularType(name, name, rowType, Array(indexName))

        val value = new TabularDataSupport(tabularType)
        x.toSeq.zipWithIndex.foreach { case (e, index) =>
          val (eKeyData, _) = fieldToElement("key", e._1)
          val (eValueData, _) = fieldToElement("value", e._2)
          val itemValues = Array[AnyRef](Int.box(index), eKeyData, eValueData)
          val cData = new CompositeDataSupport(rowType, itemNames, itemValues)
          // println(s"Created a comp data support with compositeType=${rowType}, itemNames=${itemNames}, itemValues=${itemValues}")
          value.put(cData)
        }

        value -> tabularType


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
