package blended.mgmt.repo.rest.internal

import blended.mgmt.repo.ArtifactRepo
import blended.spray.BlendedHttpRoute
import org.slf4j.LoggerFactory
import spray.http.StatusCodes
import blended.security.spray.BlendedSecuredRoute
import spray.routing.Route
import spray.http.MediaTypes

trait ArtifactRepoRoutes
    extends BlendedHttpRoute
    with BlendedSecuredRoute {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])

  protected def artifactRepos: List[ArtifactRepo]

  def getRepoFile(repo: String, path: String) = {
    artifactRepos.find(a => a.repoId == repo) match {
      case None =>
        log.debug("No repository with id: " + repo)
        complete {
          StatusCodes.NotFound -> s"No repository with id: ${repo}"
        }
      case Some(repo) =>
        log.debug("Request for path: " + path)
        repo.findFile(path) match {
          case Some(file) =>
            log.debug("Found file at path: " + path)
            getFromFile(file)
          case None =>
            log.debug("Could not find file at path: " + path)
            repo.listFiles(path) match {
              case Iterator.empty => complete(StatusCodes.NotFound)
              case files =>
                respondWithMediaType(MediaTypes.`text/html`) & complete {
                  val indexBase = repo.repoId + "/" + path
                  
                  val goUp =  if(path.isEmpty()) "" else """<br/><a href="..">..</a>"""
                  
                  val renderedFiles = files.map { f =>
                    s"""<br/><a href="${f}">${f}</a>"""
                  }.mkString

                  val html = s"""<html>
      |<head><title>Index of ${indexBase}</title></head>
      |<body>
      |<h1>Index of ${indexBase}</h1>
      |<hr>
      |${goUp}
      |${renderedFiles}
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

  override val httpRoute: Route = get {
    path(Segment) { (repoId) =>
      getRepoFile(repoId, "")
    } ~
      path(Segment / Rest) { (repoId, rest) =>
        getRepoFile(repoId, rest)
      }
  }

}