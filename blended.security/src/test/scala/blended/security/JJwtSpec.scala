package blended.security

import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.{Key, KeyStore}
import java.util.{Date, UUID}

import blended.util.FileHelper
import io.jsonwebtoken.impl.DefaultClaims
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.scalatest.{FreeSpec, Matchers}

class JJwtSpec extends FreeSpec with Matchers{

  "The JJWT library should " - {

    "Allow to create an RSA based  JWT" in {

      val content = FileHelper.readFile("/blended-mgmt.jks")

      val is = new ByteArrayInputStream(content)
      val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
      keystore.load(is, "SoU8MzmyxNmNhqg1".toCharArray())
      is.close()

      val key : Key = keystore.getKey("jwtkey", "VGSJA94MOklhavlJ".toCharArray())
      val cert : Certificate = keystore.getCertificate("jwtkey")

      val sigAlg = SignatureAlgorithm.RS256

      val permissions = List("profile-read", "profile-update")

      val id = UUID.randomUUID().toString()
      val user = "Andreas"

      val claims = new DefaultClaims()
        .setId(id)
        .setSubject(user)
        .setExpiration(new Date(System.currentTimeMillis() + 60*60*1000))
        .setIssuedAt(new Date())

      claims.put("permissions", permissions.mkString(","))

      val builder = Jwts.builder().setClaims(claims).signWith(sigAlg, key)

      val jwt = builder.compact()


      val clientClaims = Jwts.parser()
        .setSigningKey(cert.getPublicKey())
        .parseClaimsJws(jwt)

      clientClaims.getHeader.getAlgorithm() should be ("RS256")
      clientClaims.getBody.getId() should be (id)
      clientClaims.getBody.getSubject() should be (user)
      clientClaims.getBody.get("permissions", classOf[String]) should be (permissions.mkString(","))
    }
  }


}
