package blended.persistence.jdbc

import org.scalatest.FreeSpec
import scala.collection.JavaConverters._

class PersistedFieldTest extends FreeSpec {

  val testData = Seq(
    ("Null",
      Map("key" -> null).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", typeName = TypeName.Null)
      )
    ),
    ("String",
      Map("key" -> "value").asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueString = Some("value"), typeName = TypeName.String)
      )
    ),
    ("Long",
      Map("key" -> Long.MaxValue).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueLong = Some(Long.MaxValue), typeName = TypeName.Long)
      )
    ),
    (
      "Byte",
      Map("key" -> Byte.MaxValue).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueLong = Some(Byte.MaxValue), typeName = TypeName.Byte)
      )
    ), (
      "Int",
      Map("key" -> Int.MaxValue).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueLong = Some(Int.MaxValue), typeName = TypeName.Int)
      )
    ),
    ("Double",
      Map("key" -> Double.MaxValue).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueDouble = Some(Double.MaxValue), typeName = TypeName.Double)
      )
    ),
    ("Float",
      Map("key" -> Float.MaxValue).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", valueDouble = Some(Float.MaxValue), typeName = TypeName.Float)
      )
    ),
    ("List[String]",
      Map("key" -> List("v1", "v2", "v3").asJava).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", typeName = TypeName.Array),
        PersistedField(fieldId = 2, baseFieldId = Some(1), name = "0", valueString = Some("v1"), typeName = TypeName.String),
        PersistedField(fieldId = 3, baseFieldId = Some(1), name = "1", valueString = Some("v2"), typeName = TypeName.String),
        PersistedField(fieldId = 4, baseFieldId = Some(1), name = "2", valueString = Some("v3"), typeName = TypeName.String)
      )
    ),
    ("List[Map]",
      Map("key" -> List(Map("ik" -> "iv1", "ik2" -> 2).asJava, Map("second" -> "value").asJava).asJava).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", typeName = TypeName.Array),
        PersistedField(fieldId = 2, baseFieldId = Some(1), name = "0", typeName = TypeName.Object),
        PersistedField(fieldId = 3, baseFieldId = Some(2), name = "ik", valueString = Some("iv1"), typeName = TypeName.String),
        PersistedField(fieldId = 4, baseFieldId = Some(2), name = "ik2", valueLong = Some(2L), typeName = TypeName.Int),
        PersistedField(fieldId = 5, baseFieldId = Some(1), name = "1", typeName = TypeName.Object),
        PersistedField(fieldId = 6, baseFieldId = Some(5), name = "second", valueString = Some("value"), typeName = TypeName.String)
      )
    ),
    ("Config object",
      Map(
        "key1" -> "value1",
        "key2" -> List(
          "kv1",
          "kv2"
        ).asJava,
        "key3" -> Map(
          "k3a" -> "v3a",
          "k3b" -> List(
            "v3b1",
            "v3b2"
          ).asJava
        ).asJava
      ).asJava,
        Seq(
          PersistedField(fieldId = 1, name = "key1", valueString = Some("value1"), typeName = TypeName.String),
          PersistedField(fieldId = 2, name = "key2", typeName = TypeName.Array),
          PersistedField(fieldId = 3, name = "0", baseFieldId = Some(2), typeName = TypeName.String, valueString = Some("kv1")),
          PersistedField(fieldId = 4, name = "1", baseFieldId = Some(2), typeName = TypeName.String, valueString = Some("kv2")),
          PersistedField(fieldId = 5, name = "key3", typeName = TypeName.Object),
          PersistedField(fieldId = 6, name = "k3a", baseFieldId = Some(5), typeName = TypeName.String, valueString = Some("v3a")),
          PersistedField(fieldId = 7, name = "k3b", baseFieldId = Some(5), typeName = TypeName.Array),
          PersistedField(fieldId = 8, name = "0", baseFieldId = Some(7), typeName = TypeName.String, valueString = Some("v3b1")),
          PersistedField(fieldId = 9, name = "1", baseFieldId = Some(7), typeName = TypeName.String, valueString = Some("v3b2"))
        )
    ),
    (
      "LongString",
      Map("key" -> List.fill(500)("x").mkString).asJava,
      Seq(
        PersistedField(fieldId = 1, name = "key", typeName = TypeName.LongString),
        PersistedField(fieldId = 2, name = "0", baseFieldId = Some(1), typeName = TypeName.String, valueString = Some(List.fill(200)("x").mkString)),
        PersistedField(fieldId = 3, name = "1", baseFieldId = Some(1), typeName = TypeName.String, valueString = Some(List.fill(200)("x").mkString)),
        PersistedField(fieldId = 4, name = "2", baseFieldId = Some(1), typeName = TypeName.String, valueString = Some(List.fill(100)("x").mkString))
      )
    )
  )

  testData.foreach { data =>
    s"PersistedField.extractFieldsWithoutBaseId with a ${data._1} entry" in {
      assert(PersistedField.extractFieldsWithoutDataId(data._2).toSet === data._3.toSet)
    }
  }

  testData.foreach { data =>
    s"PersistedField.toJuMap with a ${data._1} entry" in {
      assert(PersistedField.toJuMap(data._3) === data._2)
    }
  }

}