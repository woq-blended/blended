package blended.persistence.jdbc

import java.{ util => ju, lang => jl }
import scala.collection.JavaConverters._
import java.util.regex.Pattern
import scala.util.Try

case class PersistedField(
  fieldId: Long = 0,
  baseFieldId: Option[Long] = None,
  name: String,
  valueLong: Option[Long] = None,
  valueDouble: Option[Double] = None,
  valueString: Option[String] = None,
  typeName: TypeName
) {

  val indexedPattern = Pattern.compile("^([\\d]+)$")

  lazy val index: Option[Long] = {
    val matcher = indexedPattern.matcher(name)
    if (matcher.matches()) {
      Some(matcher.group(1).toLong)
    } else None
  }

}

object PersistedField {

  def extractFieldsWithoutDataId(data: ju.Map[String, _ <: Any]): Seq[PersistedField] = {

    var _nextId: Long = 0L
    def nextId(): Long = { _nextId += 1; _nextId }

    def extractValue(key: String, value: Any, parent: Option[PersistedField] = None): Seq[PersistedField] = {
      val baseFieldId = parent.map(_.fieldId)
      value match {
        case value: String =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueString = Some(value), typeName = TypeName.String))
        case value: Long =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueLong = Some(value), typeName = TypeName.Long))
        case value: Int =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueLong = Some(value), typeName = TypeName.Int))
        case value: Byte =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueLong = Some(value), typeName = TypeName.Byte))
        case value: Double =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueDouble = Some(value), typeName = TypeName.Double))
        case value: Float =>
          Seq(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, valueDouble = Some(value), typeName = TypeName.Float))
        case value: ju.Map[_, _] =>
          val newBase = if (key == "" && parent.isEmpty) {
            // Root map
            None
          } else {
            Some(PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, typeName = TypeName.Object))
          }
          newBase.toSeq ++ value.entrySet().asScala.toList.flatMap { entry =>
            extractValue(entry.getKey().asInstanceOf[String], entry.getValue(), parent = newBase)
          }
        case value: ju.Collection[_] =>
          val newBase = PersistedField(fieldId = nextId(), name = key, baseFieldId = baseFieldId, typeName = TypeName.Array)
          Seq(newBase) ++ value.asScala.zipWithIndex.flatMap {
            case (v, i) =>
              extractValue(i.toString(), v, Some(newBase))
          }
        case x => throw new IllegalArgumentException("Unsupported type: " + Option(x).map(_.getClass()).orNull)
      }
    }

    extractValue(key = "", value = data)
  }

  def toJuMap(persistedFields: Seq[PersistedField]): ju.Map[String, _ <: AnyRef] = {

    def fieldExtract(field: PersistedField, others: Seq[PersistedField]): AnyRef = {
      field.typeName match {
        case TypeName.Boolean => jl.Boolean.valueOf(field.valueLong.map(_ != 0).get)
        case TypeName.Byte => jl.Byte.valueOf(field.valueLong.map(_.toByte).get)
        case TypeName.Int => jl.Integer.valueOf(field.valueLong.map(_.toInt).get)
        case TypeName.Long => jl.Long.valueOf(field.valueLong.get)
        case TypeName.String => field.valueString.get
        case TypeName.Double => jl.Double.valueOf(field.valueDouble.get)
        case TypeName.Float => jl.Float.valueOf(field.valueDouble.map(_.toFloat).get)
        case TypeName.Array =>
          val (col, colOther) = others.partition(_.baseFieldId == Some(field.fieldId))
          val collection = new ju.LinkedList[AnyRef]()
          col.foreach { colField =>
            collection.add(fieldExtract(colField, colOther))
          }
          collection
        case TypeName.Object => internMap(others, Some(field.fieldId))
      }
    }

    def internMap(fields: Seq[PersistedField], parentId: Option[Long]): ju.Map[String, _ <: AnyRef] = {
      val (root, other) = fields.partition(_.baseFieldId == parentId)
      val map = new ju.LinkedHashMap[String, AnyRef]()
      root.foreach { field =>
        val value = fieldExtract(field, other)
        map.put(field.name, value)
      }
      map
    }

    //    def internArray(fields: Seq[PersistedField], parentId: Long): ju.Collection[_ <: Any] = {
    //      val (root, other) = fields.partition(_.baseFieldId == parentId)
    //      if (!other.isEmpty) {
    //        // unsupported case: complex objects in list
    //        ???
    //      } else {
    //        val col = new ju.LinkedList[Any]()
    //        root.sortBy(_.index.getOrElse(0L)).map
    //      }
    //    }

    internMap(persistedFields, None)
  }

}
