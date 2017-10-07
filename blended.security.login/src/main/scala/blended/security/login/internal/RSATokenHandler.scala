package blended.security.login.internal

import java.security.{KeyPair, KeyPairGenerator}
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

  override def verifyToken(token: String) : Jws[Claims] = {
    Jwts.parser().setSigningKey(keyPair.getPublic()).parseClaimsJws(token)
  }
}


//"Allow to create an RSA based  JWT" in {
//
//  val content = FileHelper.readFile("/blended-mgmt.jks")
//
//  val is = new ByteArrayInputStream(content)
//  val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
//  keystore.load(is, "SoU8MzmyxNmNhqg1".toCharArray())
//  is.close()
//
//  val key : Key = keystore.getKey("jwtkey", "VGSJA94MOklhavlJ".toCharArray())
//  val cert : Certificate = keystore.getCertificate("jwtkey")
//
//  val sigAlg = SignatureAlgorithm.RS256
//
//  val permissions = List("profile-read", "profile-update")
//
//  val id = UUID.randomUUID().toString()
//  val user = "Andreas"
//
//  val claims = new DefaultClaims()
//  .setId(id)
//  .setSubject(user)
//  .setExpiration(new Date(System.currentTimeMillis() + 60*60*1000))
//  .setIssuedAt(new Date())
//
//  claims.put("permissions", permissions.mkString(","))
//
//  val builder = Jwts.builder().setClaims(claims).signWith(sigAlg, key)
//
//  val jwt = builder.compact()
//
//
//  val clientClaims = Jwts.parser()
//  .setSigningKey(cert.getPublicKey())
//  .parseClaimsJws(jwt)
//
//  clientClaims.getHeader.getAlgorithm() should be ("RS256")
//  clientClaims.getBody.getId() should be (id)
//  clientClaims.getBody.getSubject() should be (user)
//  clientClaims.getBody.get("permissions", classOf[String]) should be (permissions.mkString(","))
//}