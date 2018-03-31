package blended.security

import java.util

import com.sun.jndi.ldap.LdapCtxFactory
import javax.naming.Context
import javax.naming.directory.{InitialDirContext, SearchControls}
import javax.security.auth.login.LoginException

import scala.collection.JavaConverters._

object SimpleLDAPClient {

  private[this] val log = org.log4s.getLogger

  private[this] val ldapCfg = LDAPLoginConfig(
    url = "ldap://iqldap.kaufland:389",
    systemUser = "cn=proxy-sib,ou=proxyusers,ou=services,o=global",
    systemPassword = "ia1CZYwHjf3X4qFCpWYn",
    userBase = "o=employee",
    roleBase = "ou=sib,ou=apps,o=global"
  )

  def main(args: Array[String]) : Unit = {

    val env : Map[String, String] = Map(
      Context.INITIAL_CONTEXT_FACTORY -> classOf[LdapCtxFactory].getName(),
      Context.PROVIDER_URL -> ldapCfg.url,
      Context.SECURITY_PRINCIPAL -> ldapCfg.systemUser,
      Context.SECURITY_CREDENTIALS -> ldapCfg.systemPassword
    )

    val ctxt = new InitialDirContext(new util.Hashtable[String, Object](env.asJava))

    val constraint = new SearchControls()
    constraint.setSearchScope(SearchControls.SUBTREE_SCOPE)

    LdapSearchResult(ctxt.search(ldapCfg.userBase, "uid=mdes0309", constraint)).result match {
      case Nil => throw new LoginException("User [mdes0309] not found in LDAP.")
      case head :: _ =>
        val name = head.getNameInNamespace()
        val attr = head.getAttributes().get("cn").get()

        ctxt.addToEnvironment(Context.SECURITY_PRINCIPAL, name)
        ctxt.addToEnvironment(Context.SECURITY_CREDENTIALS, "Sitios17")

        ctxt.getAttributes("", null)

        println("User logged in")
    }

    println("Hallo Andreas")
  }
}
