package blended.security

import java.text.MessageFormat
import java.util

import blended.security.internal.{LDAPLoginConfig, LdapSearchResult}
import com.sun.jndi.ldap.LdapCtxFactory
import com.typesafe.config.Config
import javax.naming.Context
import javax.naming.directory.{DirContext, InitialDirContext, SearchControls}
import javax.security.auth.Subject
import javax.security.auth.callback.{CallbackHandler, NameCallback, PasswordCallback}
import javax.security.auth.login.LoginException
import javax.security.auth.spi.LoginModule

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class LDAPLoginModule extends LoginModule {

  private[this] val log = org.log4s.getLogger

  private[this] var subject : Option[Subject] = None
  private[this] var cbHandler : Option[CallbackHandler] = None

  private[this] var ldapContext : Option[DirContext] = None
  private[this] var loginConfig : Option[Config] = None

  private[this] var loggedInUser : Option[String] = None

  override def initialize(subject: Subject,
    callbackHandler: CallbackHandler,
    sharedState: util.Map[String, _],
    options: util.Map[String, _])
  : Unit = {

    log.info("Initialising LDAP Login module ...")

    options.get("config") match {
      case cfg : Config => loginConfig = Some(cfg)
      case other => log.warn(s"Expected configuration object of type [${classOf[Config].getName()}], got [${other.getClass().getName()}]")
    }

    ldapCfg match {
      case None => log.warn("LDAP config not available")
      case Some(_) => // do nothing
    }

    // This is the subject which needs to be enriched with the user and group information
    this.subject = Option(subject)

    // This is the callbackhandler passed in to determine the username and password
    this.cbHandler = Option(callbackHandler)
  }

  @throws[LoginException]
  override def login(): Boolean = {

    ldapCfg match {
      case None => throw new LoginException("Could not obtain LDAP configuration")
      case Some(cfg) =>

        // First of all we will obtain the directory context

        try {
          ldapContext = Some(dirContext.get)
        } catch {
          case t : Throwable =>
            log.error(t)(t.getMessage())
            throw new LoginException(t.getMessage())
        }

        log.debug(s"Successfully connected to LDAP server [${cfg.url}] user [${cfg.systemUser}]")

        cbHandler match {
          case None => throw new LoginException("No Callback Handler defined")
          case Some(cbh) =>
            loggedInUser = Some(validateUser(ldapContext.get, cfg, cbh))
            true

        }
    }
  }

  @throws[LoginException]
  override def commit(): Boolean = {
    try {
      loggedInUser match {
        case None => false
        case Some(u) =>
          subject.foreach { s =>
            s.getPrincipals().add(new UserPrincipal(u))
            val groups = getGroups(dirContext.get, ldapCfg.get, u)
            log.debug(s"Found groups [$groups] for [$u]")
            groups.foreach { g =>
              s.getPrincipals().add(new GroupPrincipal(g))
            }
          }
          dirContext.get.close()
          true
      }
    }
  }

  @throws[LoginException]
  override def abort(): Boolean = {
    loggedInUser = None
    dirContext.get.close()
    true
  }

  @throws[LoginException]
  override def logout(): Boolean = {
    loggedInUser = None
    dirContext.get.close()
    true
  }

  // obtain the initial LDAP Context
  private[this] lazy val dirContext : Try[DirContext] = Try {

    ldapCfg match {
      case None => throw new LoginException("LDAP config not available")
      case Some(cfg) =>
        try {
          var env : mutable.Map[String, String] = mutable.Map(
            Context.INITIAL_CONTEXT_FACTORY -> classOf[LdapCtxFactory].getName(),
            Context.PROVIDER_URL -> cfg.url
          )

          env ++= cfg.systemUser.map(u => (Context.SECURITY_PRINCIPAL -> u))
          env ++= cfg.systemPassword.map(u => (Context.SECURITY_PRINCIPAL -> u))

          new InitialDirContext(new util.Hashtable[String, Object](env.asJava))
        } catch {
          case t : Throwable =>
            log.error(t)(t.getMessage())
            throw new LoginException(t.getMessage())
        }
    }
  }

  // convenience to extract the LDAP Config object
  lazy val ldapCfg : Option[LDAPLoginConfig] = loginConfig.map(c => LDAPLoginConfig.fromConfig(c))

  // convenience method to encode a filter string
  def doRFC2254Encoding(inputString : String) : String = inputString match {
    case s if s.isEmpty() => ""
    case s if s.startsWith("\\") => "\\5c" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("*") => "\\2a" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("(") => "\\28" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith(")") => "\\29" + doRFC2254Encoding(s.substring(1))
    case s if s.startsWith("\0") => "\\00" + doRFC2254Encoding(s.substring(1))
    case s => s.substring(0,1) + doRFC2254Encoding(s.substring(1))
  }

  @throws[LoginException]
  private[this] def validateUser(
    ctxt : DirContext,
    cfg : LDAPLoginConfig,
    cbHandler : CallbackHandler
  ) : String = {

    try {

      val nameCallback = new NameCallback("User: ")
      val passwordCallback = new PasswordCallback("Password: ", false)
      cbHandler.handle(Array(nameCallback, passwordCallback))

      val user = nameCallback.getName()
      val pwd = new String(passwordCallback.getPassword())
      log.info(s"Authenticating user [$user]")

      val constraint = new SearchControls()
      constraint.setSearchScope(SearchControls.SUBTREE_SCOPE)

      val userSearchFormat = new MessageFormat(cfg.userSearch)
      val filter = userSearchFormat.format(Array(doRFC2254Encoding(user)))

      val r = ctxt.search(cfg.userBase, filter, constraint)

      LdapSearchResult(r).result match {
        case Nil => throw new LoginException(s"User [$user] not found in LDAP.")
        case head :: tail =>
          if (tail.length > 0) {
            log.warn(s"Search for user [$user] returned [${1 + tail.length}] records, using first record only.")
          }

          val name = head.getNameInNamespace()

          ctxt.addToEnvironment(Context.SECURITY_PRINCIPAL, name)
          ctxt.addToEnvironment(Context.SECURITY_CREDENTIALS, pwd)

          ctxt.getAttributes("", null)
          log.info(s"User [$user] authenticated with LDAP name [$name]")
          name
      }
    } catch {
      case t : Throwable =>
        log.error(t)(t.getMessage())
        throw new LoginException(t.getMessage())
    } finally {
      cfg.systemUser match {
        case None => ctxt.removeFromEnvironment(Context.SECURITY_PRINCIPAL)
        case Some(u) => ctxt.addToEnvironment(Context.SECURITY_PRINCIPAL, u)
      }
      cfg.systemPassword match {
        case None => ctxt.removeFromEnvironment(Context.SECURITY_CREDENTIALS)
        case Some(p) => ctxt.addToEnvironment(Context.SECURITY_CREDENTIALS, p)
      }
    }
  }

  @throws[LoginException]
  private[this] def getGroups(ctxt: DirContext, cfg: LDAPLoginConfig, member : String) : List[String] = {
    val constraint = new SearchControls()
    constraint.setSearchScope(SearchControls.SUBTREE_SCOPE)

    val groupSearchFormat = new MessageFormat(cfg.groupSearch)
    val filter = groupSearchFormat.format(Array(doRFC2254Encoding(member)))

    val r = ctxt.search(cfg.groupBase, filter, constraint)

    LdapSearchResult(r).result.map { sr =>
      sr.getAttributes().get(cfg.groupAttribute).get().toString()
    }
  }
}
