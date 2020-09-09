package blended.updater.tools.configbuilder

import java.io.File

import blended.updater.config.{Artifact, MvnGav, Profile}

object MvnGavSupport {

  def downloadUrls(mvnGavs: Seq[(MvnGav, String)], artifact: Artifact, debug: Boolean): Option[String] = {
    // lookup in GAV
    val mvnGav = MvnGav.parse(artifact.url.substring(Profile.MvnPrefix.length()))
    val directUrl = mvnGavs
      .find {
        case (gav, _) => mvnGav.toOption.filter { _ == gav }.isDefined
      }
      .map {
        case (_, file) => new File(file).toURI().toString()
      }

    if (debug && !mvnGavs.isEmpty && directUrl.isEmpty) {
      Console.err.println(s"Could not find artifact [${mvnGav}] in given artifact list")
      if (mvnGav.isSuccess) {
        val search = mvnGav.get
        val otherVersions = mvnGavs.filter {
          case (gav, _) =>
            gav.group == search.group && gav.artifact == search.artifact && gav.classifier == search.classifier && gav.fileExt == search.fileExt
        }
        if (!otherVersions.isEmpty) {
          Console.err.println(s"Found artifacts with different versions: ${otherVersions.mkString(",")}")
        }
      }
    }

    directUrl
  }

}
