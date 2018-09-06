package blended.security.login.impl

import java.security.{KeyPair, KeyPairGenerator, PublicKey}
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

import blended.security.BlendedPermissions
import blended.security.boot.{GroupPrincipal, UserPrincipal}
import blended.security.json.PrickleProtocol._
import blended.security.login.api.{Token, TokenHandler}
import io.jsonwebtoken.impl.DefaultClaims
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import javax.security.auth.Subject
import prickle.Pickle

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object RSATokenHandler {

  def apply(): RSATokenHandler = {

    val keygen = KeyPairGenerator.getInstance("RSA")
    keygen.initialize(2048)

    new RSATokenHandler(keygen.generateKeyPair())
  }

  val counter : AtomicLong = new AtomicLong(0)
}

class RSATokenHandler(keyPair : KeyPair) extends TokenHandler {

  override def createToken(id: String, subj: Subject, expire : Option[FiniteDuration], permissions: BlendedPermissions): Try[Token] = Try {

    val date : Date = new Date()
    val expiresAt : Option[Date] = expire.map{ d => new Date(date.getTime + d.toMillis) }

    val claims = new DefaultClaims()
      .setId(id)
      .setSubject(id)
      .setIssuedAt(date)

    val user : UserPrincipal = subj.getPrincipals(classOf[UserPrincipal]).asScala.toList match {
      case Nil => throw new Exception("Subject is missing the User Principal")
      case h :: _ => h
    }

    val groups : List[GroupPrincipal] = subj.getPrincipals(classOf[GroupPrincipal]).asScala.toList

    claims.put("user", user.getName())
    claims.put("groups", groups.map(_.getName()).mkString(","))
    if (groups.nonEmpty) claims.put("permissions", Pickle.intoString(permissions))

    expiresAt.foreach { claims.setExpiration }

    val token = Jwts.builder()
      .setClaims(claims)
      .signWith(SignatureAlgorithm.RS512, keyPair.getPrivate())
      .compact()

    Token(
      id,
      user,
      groups,
      expiresAt.getOrElse(new Date(0)).getTime,
      permissions,
      token
    )
  }

  override def publicKey(): PublicKey = keyPair.getPublic()
}
