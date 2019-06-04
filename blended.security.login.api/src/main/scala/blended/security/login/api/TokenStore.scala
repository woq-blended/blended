package blended.security.login.api

import java.security.PublicKey

import blended.security.BlendedPermissions
import blended.security.boot.{GroupPrincipal, UserPrincipal}
import io.jsonwebtoken.Jwts
import javax.security.auth.Subject
import prickle.Unpickle
import blended.security.json.PrickleProtocol._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Token {
  def apply(webToken : String, key : PublicKey) : Try[Token] = Try {
    val claims = Jwts.parser().setSigningKey(key).parseClaimsJws(webToken)

    val permissionsJson = claims.getBody().get("permissions", classOf[String])
    val permissions = Unpickle[BlendedPermissions].fromString(permissionsJson)

    val user : UserPrincipal = new UserPrincipal(claims.getBody().get("user", classOf[String]))
    val groups : List[GroupPrincipal] = Option(
      claims.getBody().get("groups", classOf[String])
    ) match {
        case None    => List.empty
        case Some(s) => s.split(",").map(sg => new GroupPrincipal(sg)).toList
      }

    Token(
      claims.getBody.getId,
      user,
      groups,
      Option(claims.getBody.getExpiration).map(_.getTime).getOrElse(0),
      permissions.get,
      webToken = webToken
    )
  }
}
/**
 * A class encapsulating a security token identified by a unique key
 * @param id the key to identify the token
 * @param expiresAt the timestamp when this particular token will expire, if 0 it will never expire
 * @param webToken The token String, this is normally the compact form ao a JSON Web Token
 */
case class Token(
  id : String,
  user : UserPrincipal,
  groups : List[GroupPrincipal],
  expiresAt : Long,
  permissions : BlendedPermissions,
  webToken : String
)

trait TokenStore {

  /**
   * Retrieve a token from the store with its identifier.
   * @param id the identifier to retrieve the token
   * @return the token if it exists or None, wrapped in a Future
   */
  def getToken(id : String) : Option[Token]

  /**
   * Remove a token from the store with its identifier.
   * @param id The identifier identifying the token to be removed
   * @return the token that has been removed, if it was present or None, wrapped in a Future
   */
  def removeToken(id : String) : Option[Token]

  def removeAllTokens() : Unit

  /**
   * Store a given token in the store. If the token already exists, an exception is thrown.
   * @param token The token to be stored
   * @return Success(token) if the store was successful, Failure(_) otherwise, wrapped in a Future.
   */
  def storeToken(token : Token) : Try[Token]
  /**
   * Create a token with a given user name / password.
   * The implementation will use the standard container LoginContext to perform a login()
   * using the credentials provided. Afterwards, we use a [[blended.security.BlendedPermissionManager]] to
   * map the [[blended.security.boot.GroupPrincipal]]s contained in the subject to permissions.
   * Finally, the token will be created for the user with the retrieved permissions.
   * @param user the username, which will identify the new token
   * @param ttl The validatity for the new token
   * @return Success(token) if the token can be created, Failure(_) otherwise, wrapped in a Future
   */
  def newToken(subj : Subject, ttl : Option[FiniteDuration])(implicit eCtxt : ExecutionContext) : Try[Token]

  def listTokens() : Seq[Token]

  def publicKey() : PublicKey

  def verifyToken(token : String) : Try[Token]

}
