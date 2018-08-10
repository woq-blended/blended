package blended.security.login

import java.security.PublicKey

import io.jsonwebtoken.{Claims, Jws, Jwts}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * A class encapsulating a security token identified by a unique key
  * @param id the key to identify the token
  * @param expiresAt the timestamp when this particular token will expire, if 0 it will never expire
  * @param webToken The token String, this is normally the compact form ao a JSON Web Token
  */
case class Token(
  id : String,
  expiresAt : Long,
  webToken : String
)

trait TokenStore {

  /**
   * Retrieve a token from the store with its identifier.
    * @param id the identifier to retrieve the token
    * @return the token if it exists or None, wrapped in a Future
   */
  def getToken(id: String) : Future[Option[Token]]

  /**
    * Remove a token from the store with its identifier.
    * @param id The identifier identifying the token to be removed
    * @return the token that has been removed, if it was present or None, wrapped in a Future
    */
  def removeToken(id: String) : Future[Option[Token]]

  /**
    * Store a given token in the store. If the token already exists, an exception is thrown.
    * @param token The token to be stored
    * @return Success(token) if the store was successful, Failure(_) otherwise, wrapped in a Future.
    */
  def storeToken(token : Token) : Future[Try[Token]]
  /**
    * Create a token with a given user name / password.
    * The implementation will use the standard container LoginContext to perform a login()
    * using the credentials provided. Afterwards, we use a [[blended.security.BlendedPermissionManager]] to
    * map the [[blended.security.boot.GroupPrincipal]]s contained in the subject to permissions.
    * Finally, the token will be created for the user with the retrieved permissions.
    * @param user the username, which will identify the new token
    * @param password the password for the user
    * @param ttl The validatity for the new token
    * @return Success(token) if the token can be created, Failure(_) otherwise, wrapped in a Future
    */
  def newToken(user : String, password: Array[Char], ttl: Option[FiniteDuration]) : Future[Try[Token]]

  def listTokens() : Future[Seq[Token]]

  def publicKey() : PublicKey

   def verifyToken(token: String) : Jws[Claims] = {
    Jwts.parser().setSigningKey(publicKey()).parseClaimsJws(token)
  }

}
