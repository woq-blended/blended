package blended.security

import java.util.{Date, UUID}
import javax.crypto.spec.SecretKeySpec

import io.jsonwebtoken.impl.DefaultClaims
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.scalatest.FreeSpec

class JJwtSpec extends FreeSpec {

  "The JJWT library should " - {

    "Allow to create a Json Web Token" in {

      val sigAlg = SignatureAlgorithm.HS256

      val secret : Array[Byte] = "secret".getBytes()

      val permissions = List("profile-read", "profile-update")

      val claims = new DefaultClaims()
        .setId(UUID.randomUUID().toString())
        .setSubject("Andreas")
        .setExpiration(new Date(System.currentTimeMillis() + 60*60*1000))
        .setIssuedAt(new Date())

      claims.put("permissions", permissions.mkString(","))

      val signKey = new SecretKeySpec(secret, sigAlg.getJcaName())
      val builder = Jwts.builder().setClaims(claims).signWith(sigAlg, signKey)

      println(builder.compact())

      succeed
    }
  }


}
