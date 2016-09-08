package blended.mgmt.repo.rest

import spray.routing.HttpService
import spray.routing.Route
import blended.mgmt.repo.ArtifactRepo
import org.slf4j.LoggerFactory
import spray.http.StatusCodes

/**
 * To be used in [[ArtifactRepoServlet]]
 */
trait ArtifactRepoRoutes extends HttpService {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])

  protected def artifactRepo: ArtifactRepo

  def route: Route = get { r =>
    val path = r.unmatchedPath.toString()
    log.debug("Request for path: {}", path)

    artifactRepo.findFile(path) match {
      case Some(file) => getFromFile(file)
      case None => complete(StatusCodes.NotFound)
    }
  }

}