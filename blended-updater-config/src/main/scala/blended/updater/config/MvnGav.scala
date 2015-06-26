package blended.updater.config

import java.util.regex.Pattern
import scala.util.Try

case class MvnGav(group: String, artifact: String, version: String) {
  import MvnGav._

  def toUrl(baseUrl: String): String = {
    val sep = if (baseUrl.isEmpty() || baseUrl.endsWith("/")) "" else "/"
    val groupPath = GroupIdToPathPattern.matcher(group).replaceAll("/")
    s"${baseUrl}${sep}${groupPath}/${artifact}/${version}/${artifact}-${version}.jar"
  }
}

object MvnGav {

  val GroupIdToPathPattern = Pattern.compile("[.]")
  val ParsePattern = Pattern.compile("([^:]+)[:]([^:]+)[:]([^:]+)")

  def toUrl(baseUrl: String)(mvnUrl: MvnGav): String = mvnUrl.toUrl(baseUrl)

  def parse(gav: String): Try[MvnGav] = Try {
    val m = ParsePattern.matcher(gav)
    if (m.matches()) {
      MvnGav(m.group(1), m.group(2), m.group(3))
    } else {
      sys.error("Invalid GAV coordinates: " + gav)
    }
  }

}
