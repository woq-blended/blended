package blended.mgmt.repo.rest.internal

import blended.mgmt.repo.ArtifactRepo
import blended.spray.BlendedHttpRoute
import org.slf4j.LoggerFactory
import spray.http.StatusCodes
import blended.security.spray.BlendedSecuredRoute

trait ArtifactRepoRoutes
    extends BlendedHttpRoute
    with BlendedSecuredRoute {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])

  protected def artifactRepo: ArtifactRepo

  override val httpRoute = get { r =>
    requirePermission("artifact:get") {
      val path = r.unmatchedPath.toString()
      log.debug("Request for path: {}", path)

      artifactRepo.findFile(path) match {
        case Some(file) => getFromFile(file)
        case None => complete(StatusCodes.NotFound)
      }
    }
  }

}