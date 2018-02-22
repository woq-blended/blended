package blended.util.config

import org.scalatest.FreeSpec
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

class ConfigDefaultGetterSpec extends FreeSpec with ConfigDefaultGetter {

  val config1 =
    """|string1 = val1
       |int1 = 1
       |long1 = 1
       |boolean1 = true
       |boolean2 = false
       |key2 {
       |  key3 = val3
       |  key4 = [ val4-1, val4-2 ]
       |}
       |""".stripMargin

  val config2 = ""

  val present = ConfigFactory.parseString(config1)
  val missing = ConfigFactory.parseString(config2)
  var count = 0
  def nextCount = { count += 1; count }

  def checkGetter[T](typeName: String, existing: T, default: T, f: (Config, T) => T): Unit = {
    require(existing != default, "The test case makes only sense, if existing and default value are not the same")
    s"${nextCount}. Implicit get${typeName} with default should" - {
      "return the present value" in {
        assert(f(present, default) === existing)
      }
      "return the default value for missing entry" in {
        assert(f(missing, default) === default)
      }
    }
  }

  checkGetter[String]("String", "val1", "default", (c, d) => c.getString("string1", d))
  checkGetter[Int]("Int", 1, 2, (c, d) => c.getInt("int1", d))
  checkGetter[Long]("Long", 1L, 2L, (c, d) => c.getLong("long1", d))
  checkGetter[Boolean]("Boolean", true, false, (c, d) => c.getBoolean("boolean1", d))
  checkGetter[Boolean]("Boolean", false, true, (c, d) => c.getBoolean("boolean2", d))
  checkGetter[List[String]]("StringList", List("val4-1", "val4-2"), List("default"), (c, d) => c.getStringList("key2.key4", d))
  
}
