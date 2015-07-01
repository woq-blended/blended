package blended.updater.config

import java.util.regex.Pattern
import scala.util.Try

case class MvnGav(
    group: String,
    artifact: String,
    version: String,
    classifier: Option[String] = None,
    fileExt: String = "jar") {
  import MvnGav._

  def toUrl(baseUrl: String): String = {
    val sep = if (baseUrl.isEmpty() || baseUrl.endsWith("/")) "" else "/"
    val groupPath = GroupIdToPathPattern.matcher(group).replaceAll("/")
    val classifierPart = classifier.map(c => s"-${c}").getOrElse("")
    s"${baseUrl}${sep}${groupPath}/${artifact}/${version}/${artifact}-${version}${classifierPart}.${fileExt}"
  }
}

object MvnGav {

  val GroupIdToPathPattern = Pattern.compile("[.]")
  val ParseCompactPattern = Pattern.compile("([^:]+)[:]([^:]+)[:]([^:]+)")
  val ParseFullPattern = Pattern.compile("([^:]+)[:]([^:]+)[:]([^:]+)[:]([^:]+)([:]([^:]+))?")

  def toUrl(baseUrl: String)(mvnUrl: MvnGav): String = mvnUrl.toUrl(baseUrl)

  def parse(gav: String): Try[MvnGav] = Try {
    val m = ParseCompactPattern.matcher(gav)
    if (m.matches()) {
      MvnGav(m.group(1), m.group(2), m.group(3))
    } else {
      val m2 = ParseFullPattern.matcher(gav)
      if (m2.matches()) {
        val classifier = m2.group(3) match {
          case "" => None
          case c => Some(c)
        }
        val fileExt = m2.group(6) match {
          case null | "" if classifier == Some("pom") => "pom"
          case null | "" => "jar"
          case t => t
        }
        MvnGav(m2.group(1), m2.group(2), m2.group(4), classifier = classifier, fileExt = fileExt)
      } else
        sys.error("Invalid GAV coordinates: " + gav)
    }
  }

}
