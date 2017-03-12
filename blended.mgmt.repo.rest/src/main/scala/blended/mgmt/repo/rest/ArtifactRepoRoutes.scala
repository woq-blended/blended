package blended.mgmt.repo.rest

import blended.mgmt.repo.ArtifactRepo
import blended.spray.BlendedHttpRoute
import org.slf4j.LoggerFactory
import spray.http.StatusCodes

trait ArtifactRepoRoutes extends BlendedHttpRoute {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])

  protected def artifactRepo: ArtifactRepo

  override val httpRoute = get { r =>
    val path = r.unmatchedPath.toString()
    log.debug("Request for path: {}", path)

    artifactRepo.findFile(path) match {
      case Some(file) => getFromFile(file)
      case None => complete(StatusCodes.NotFound)
    }
  }

}