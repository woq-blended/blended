package blended.security.login.internal

import java.security.{KeyPair, KeyPairGenerator, PublicKey}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

import blended.security.login.TokenHandler
import io.jsonwebtoken.impl.DefaultClaims
import io.jsonwebtoken.{Claims, Jws, Jwts, SignatureAlgorithm}

import scala.concurrent.duration.FiniteDuration

object RSATokenHandler {

  def apply(): RSATokenHandler = {

    val keygen = KeyPairGenerator.getInstance("RSA")
    keygen.initialize(2048)

    new RSATokenHandler(keygen.generateKeyPair())
  }

  val counter : AtomicLong = new AtomicLong(0)
}

class RSATokenHandler(keyPair : KeyPair) extends TokenHandler {

  val df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")

  override def createToken(user: String, expire : Option[FiniteDuration], permission: String*): String = {

    val date = new Date()

    val id = df.format(date) + "-" + RSATokenHandler.counter.incrementAndGet()

    val claims = new DefaultClaims()
      .setId(id)
      .setSubject(user)
      .setIssuedAt(date)

    claims.put("permissions", permission.mkString(","))

    expire.foreach{exp =>
      claims.setExpiration(new Date(date.getTime() + exp.toMillis))
    }

    Jwts.builder()
      .setClaims(claims)
      .signWith(SignatureAlgorithm.RS512, keyPair.getPrivate())
      .compact()
  }

  override def publicKey(): PublicKey = keyPair.getPublic()
}
