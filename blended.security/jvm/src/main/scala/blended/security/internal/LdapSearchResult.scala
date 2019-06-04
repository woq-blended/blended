package blended.security.internal

import javax.naming.NamingEnumeration
import javax.naming.directory.SearchResult

import scala.collection.mutable.ListBuffer

case class LdapSearchResult(private val sr : NamingEnumeration[SearchResult]) {

  lazy val result : List[SearchResult] = {
    val r : ListBuffer[SearchResult] = ListBuffer.empty
    while (sr.hasMore()) {
      r += sr.next()
    }
    r.toList
  }

}
