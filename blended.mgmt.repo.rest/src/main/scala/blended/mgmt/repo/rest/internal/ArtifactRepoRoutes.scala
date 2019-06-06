package blended.mgmt.repo.rest.internal

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.mgmt.repo.ArtifactRepo
import blended.security.akka.http.BlendedSecurityDirectives
import blended.util.logging.Logger

trait ArtifactRepoRoutes {
  deps : BlendedSecurityDirectives =>

  private[this] val log = Logger[ArtifactRepoRoutes]

  protected def artifactRepos : List[ArtifactRepo]

  def getRepoFile(repo : String, path : String) : Route = {
    artifactRepos.find(a => a.repoId == repo) match {
      case None =>
        log.debug(s"No repository with id: [$repo]")
        complete {
          StatusCodes.NotFound -> s"No repository with id: [$repo]"
        }
      case Some(repository) =>
        log.debug("Request for path: " + path)
        repository.findFile(path) match {
          case Some(file) =>
            log.debug(s"Found file at path: [$path]")
            getFromFile(file)
          case None =>
            log.debug(s"Could not find file at path: [$path]")
            repository.listFiles(path) match {
              case Iterator.empty => complete(StatusCodes.NotFound)
              case files =>
                //                respondWithMediaType(MediaTypes.`text/html`) &
                complete {
                  val indexBase = repository.repoId + "/" + path

                  val goUp = if (path.isEmpty()) "" else """<br/><a href="..">..</a>"""

                  val renderedFiles = files.map { f =>
                    s"""<br/><a href="$f">$f</a>"""
                  }.mkString

                  val html = s"""|<html>
                    |<head><title>Index of $indexBase</title></head>
                    |<body>
                    |<h1>Index of $indexBase</h1>
                    |<hr>
                    |$goUp
                    |$renderedFiles
                    |<hr>
                    |</body>
                    |</html>
                    |""".stripMargin

                  html
                }
            }
        }
    }
  }

  val httpRoute : Route = get {
    path(Segment) { repoId =>
      getRepoFile(repoId, "")
    } ~
      path(Segment / Remaining) { (repoId, rest) =>
        getRepoFile(repoId, rest)
      }
  }

}
