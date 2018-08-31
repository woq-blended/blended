package blended.mgmt.repo.rest.internal

import blended.mgmt.repo.ArtifactRepo
import blended.security.BlendedPermissionManager
import blended.security.akka.http.JAASSecurityDirectives
import blended.util.logging.Logger

class ArtifactRepoRoutesImpl(override val mgr : BlendedPermissionManager)
  extends ArtifactRepoRoutes
  with JAASSecurityDirectives {

  private[this] val log = Logger[ArtifactRepoRoutesImpl]

  private[this] var repos: List[ArtifactRepo] = List()

  override protected def artifactRepos: List[ArtifactRepo] = repos

  def addRepo(repo: ArtifactRepo): Unit = {
    log.debug(s"Registering artifactRepo: ${repo} to ${this}")
    repos = repo :: artifactRepos
    log.debug(s"known repos: ${artifactRepos}")
  }

  def removeRepo(repo: ArtifactRepo): Unit = {
    log.debug(s"Unregistering artifactRepo: ${repo} from ${this}")
    repos = repos.filter(r => r.repoId != repo.repoId)
    log.debug(s"known repos: ${artifactRepos}")
  }
  
  def clearRepos(): Unit = {
    repos = List()
  }

}
