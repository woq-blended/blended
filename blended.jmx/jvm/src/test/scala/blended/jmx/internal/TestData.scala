package blended.jmx.internal

import java.{lang => jl}
import java.{math => jm}

import javax.management.ObjectName
import org.scalacheck.{Arbitrary, Gen}

trait TestData {

  implicit val arbObjectNames: Arbitrary[ObjectName] = Arbitrary {
    for {
      domain <- Gen.identifier
      name <- Gen.identifier
    } yield new ObjectName(s"${domain}:type=${name}")
  }

  implicit val arbJmBigDecimal: Arbitrary[jm.BigDecimal] = Arbitrary {
    for (b <- Arbitrary.arbitrary[BigDecimal]) yield b.bigDecimal
  }
  implicit val arbJmBigInteger: Arbitrary[jm.BigInteger] = Arbitrary {
    for (b <- Arbitrary.arbitrary[BigInt]) yield b.bigInteger
  }
  implicit val arbBoolean: Arbitrary[jl.Boolean] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Boolean]) yield jl.Boolean.valueOf(b)
  }
  implicit val arbByte: Arbitrary[jl.Byte] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Byte]) yield jl.Byte.valueOf(b)
  }
  implicit val arbShort: Arbitrary[jl.Short] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Short]) yield jl.Short.valueOf(b)
  }
  implicit val arbInteger: Arbitrary[jl.Integer] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Int]) yield jl.Integer.valueOf(b)
  }
  implicit val arbLong: Arbitrary[jl.Long] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Long]) yield jl.Long.valueOf(b)
  }
  implicit val arbFloat: Arbitrary[jl.Float] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Float]) yield jl.Float.valueOf(b)
  }
  implicit val arbDouble: Arbitrary[jl.Double] = Arbitrary {
    for(b <- Arbitrary.arbitrary[Double]) yield jl.Double.valueOf(b)
  }
  implicit val arbVoid: Arbitrary[Void] = Arbitrary {
    Gen.const(null)
  }

}

object TestData extends TestData
