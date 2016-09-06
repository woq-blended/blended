package blended.mgmt.repo.internal

import java.io.File

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigException

import blended.domino.TypesafeConfigWatching
import blended.mgmt.repo.file.FileArtifactRepo
import domino.DominoActivator
import blended.mgmt.repo.ArtifactRepo

class ArtifactRepoActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoActivator])

  whenBundleActive {
    log.debug("About to activate bundle: {}", bundleContext.getBundle().getSymbolicName())

    whenTypesafeConfigAvailable { (config, idService) =>
      log.debug("Config: {}", config)

      try {
        val repoId = config.getString("repoId")

        val baseDir = new File(config.getString("baseDir")).getAbsoluteFile()
        if (!baseDir.exists()) {
          log.warn("baseDir for artifact repository does not exists: {}", baseDir)
        }

        val repoService = new FileArtifactRepo(repoId, baseDir)
        log.debug("Service: {}", repoService)

        log.debug("About to register file based artifact repository to OSGi service registry")
        repoService.providesService[ArtifactRepo](
          "repoId" -> repoId,
          "baseDir" -> baseDir.getAbsolutePath()
        )

      } catch {
        case e: ConfigException =>
          log.error("Missing configuration to provide file base artifact repository", e)
      }

    }
  }

}