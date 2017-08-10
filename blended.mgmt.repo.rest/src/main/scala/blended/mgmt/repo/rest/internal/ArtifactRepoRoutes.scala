package blended.mgmt.repo.rest.internal

import blended.mgmt.repo.ArtifactRepo
import blended.spray.BlendedHttpRoute
import org.slf4j.LoggerFactory
import spray.http.StatusCodes
import blended.security.spray.BlendedSecuredRoute
import spray.routing.Route

trait ArtifactRepoRoutes
    extends BlendedHttpRoute
    with BlendedSecuredRoute {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])

  protected def artifactRepos: List[ArtifactRepo]

  override val httpRoute: Route = pathPrefix(Segment) { id =>
    artifactRepos.find(a => a.repoId == id) match {
      case None =>
        log.debug("No repository with id: {}", id)
        complete(StatusCodes.NotFound)
      case Some(repo) =>
        log.debug("Selected repository with id: {}", id)
        get { r =>
          requirePermission("artifact:get") {
            val path = r.unmatchedPath.toString()
            log.debug("Request for path: {}", path)
            repo.findFile(path) match {
              case Some(file) => getFromFile(file)
              case None =>
                repo.findFiles(path) match {
                  case Iterator.empty => complete(StatusCodes.NotFound)
                  case files => complete("<ul>" + files.mkString("<li>", "</li>\n<li>", "</li>") + "</ul>")
                }
            }
          }
        }
    }
  }

}