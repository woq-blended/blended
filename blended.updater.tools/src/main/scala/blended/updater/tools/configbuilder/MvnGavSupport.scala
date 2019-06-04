package blended.updater.tools.configbuilder

import blended.updater.config.Artifact
import blended.updater.config.MvnGav
import blended.updater.config.RuntimeConfig
import java.io.File

object MvnGavSupport {

  def downloadUrls(mvnGavs : Seq[(MvnGav, String)], artifact : Artifact, debug : Boolean) : Option[String] = {
    // lookup in GAV
    val mvnGav = MvnGav.parse(artifact.url.substring(RuntimeConfig.MvnPrefix.length()))
    val directUrl = mvnGavs.find {
      case (gav, _) => mvnGav.toOption.filter { _ == gav }.isDefined
    }.map {
      case (_, file) => new File(file).toURI().toString()
    }

    if (debug && !mvnGavs.isEmpty && directUrl.isEmpty) {
      Console.err.println(s"Could not find artifact [${mvnGav}] in given artifact list")
      if (mvnGav.isSuccess) {
        val search = mvnGav.get
        val otherVersions = mvnGavs.filter { case (gav, _) => gav.group == search.group && gav.artifact == search.artifact && gav.classifier == search.classifier && gav.fileExt == search.fileExt }
        if (!otherVersions.isEmpty) {
          Console.err.println(s"Found artifacts with different versions: ${otherVersions.mkString(",")}")
        }
      }
    }

    directUrl
  }

}
