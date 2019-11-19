package blended.jmx

import blended.util.RichTry._
import javax.management.ObjectName
import javax.management.openmbean.{CompositeData, TabularData}

import scala.collection.JavaConverters._
import scala.util.Try

object JmxAttributeCompanion {

  def lift(v : Any) : Try[AttributeValue] = Try {

    Option(v) match {
      case None => UnitAttributeValue()
      case Some(o) =>
        o match {
          case u : Unit                  => UnitAttributeValue()
          case s : String                => StringAttributeValue(s)
          case i : java.lang.Integer     => IntAttributeValue(i)
          case l : java.lang.Long        => LongAttributeValue(l)
          case b : java.lang.Boolean     => BooleanAttributeValue(b)
          case b : java.lang.Byte        => ByteAttributeValue(b)
          case s : java.lang.Short       => ShortAttributeValue(s)
          case f : java.lang.Float       => FloatAttributeValue(f)
          case d : java.lang.Double      => DoubleAttributeValue(d)
          case bi : java.math.BigInteger => BigIntegerAtrributeValue(bi)
          case bd : java.math.BigDecimal => BigDecimalAtrributeValue(bd)

          case n : ObjectName            => ObjectNameAttributeValue(JmxObjectNameCompanion.createJmxObjectName(n).unwrap)

          case al : Array[Long]          => ListAttributeValue(al.map(LongAttributeValue.apply).toList)
          case ab : Array[Boolean]       => ListAttributeValue.apply(ab.map(BooleanAttributeValue.apply).toList)
          case ab : Array[Byte]          => ListAttributeValue.apply(ab.map(ByteAttributeValue.apply).toList)
          case ac : Array[Char]          => ListAttributeValue.apply(ac.map(CharAttributeValue.apply).toList)
          case ad : Array[Double]        => ListAttributeValue.apply(ad.map(DoubleAttributeValue.apply).toList)
          case af : Array[Float]         => ListAttributeValue.apply(af.map(FloatAttributeValue.apply).toList)
          case ai : Array[Int]           => ListAttributeValue.apply(ai.map(IntAttributeValue.apply).toList)
          case as : Array[Short]         => ListAttributeValue.apply(as.map(ShortAttributeValue.apply).toList)
          case a : Array[Any]            => ListAttributeValue(a.map(v => lift(v).unwrap).toList)

          case t : TabularData =>
            val values : List[AttributeValue] = t.values().asScala.map(v => lift(v).get).toList
            TabularAttributeValue(values)

          case cd : CompositeData =>
            val map : Map[String, AttributeValue] = cd.getCompositeType().keySet().asScala.map { key =>
              (key, lift(cd.get(key)).get)
            }.toMap
            CompositeAttributeValue(map)

          case _ =>
            throw new IllegalArgumentException(s"Unsupported Attribute type [${o.getClass().getName()}]")
        }
    }
  }
}
