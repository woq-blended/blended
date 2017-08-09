package blended.mgmt.repo.rest.internal

import blended.mgmt.repo.ArtifactRepo
import blended.spray.BlendedHttpRoute
import org.slf4j.LoggerFactory
import spray.http.StatusCodes
import blended.security.spray.BlendedSecuredRoute
import spray.routing.Route

trait ArtifactRepoRoutes
    extends BlendedHttpRoute
    with BlendedSecuredRoute { self =>

  private[this] val log = {
    val log = LoggerFactory.getLogger(classOf[ArtifactRepoRoutes])
    log.debug("Creating {}", self)
    log
  }

  protected def artifactRepo: ArtifactRepo

  override val httpRoute: Route = get { r =>
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