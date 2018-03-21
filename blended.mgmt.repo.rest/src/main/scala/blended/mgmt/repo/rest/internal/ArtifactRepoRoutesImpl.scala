package blended.mgmt.repo.rest.internal

import blended.security.akka.http.ShiroBlendedSecurityDirectives
import org.apache.shiro.mgt.SecurityManager
import blended.mgmt.repo.ArtifactRepo

class ArtifactRepoRoutesImpl(
  override val securityManager: Option[SecurityManager]
)
  extends ArtifactRepoRoutes
  with ShiroBlendedSecurityDirectives {

  private[this] val log = org.log4s.getLogger

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