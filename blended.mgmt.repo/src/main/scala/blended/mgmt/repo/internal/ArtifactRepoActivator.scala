package blended.mgmt.repo.internal

import java.io.File

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigException

import blended.domino.TypesafeConfigWatching
import blended.mgmt.repo.file.FileArtifactRepo
import domino.DominoActivator
import blended.mgmt.repo.ArtifactRepo

class ArtifactRepoActivator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoActivator])

  whenBundleActive {
    log.info("About to activate bundle: {}", bundleContext.getBundle().getSymbolicName())

    whenTypesafeConfigAvailable { (config, idService) =>
      log.debug("Config: {}", config)

      try {
        val repoId = config.getString("repoId")

        val baseDir = new File(config.getString("baseDir")).getAbsoluteFile()
        if (!baseDir.exists()) {
          log.warn("baseDir for artifact repository does not exists: {}", baseDir)
        }

        val repoService = new FileArtifactRepo(repoId, baseDir)
        log.info("Created service: {}", repoService)

        log.debug("About to register file based artifact repository to OSGi service registry")
        repoService.providesService[ArtifactRepo](
          "repoId" -> repoId,
          "baseDir" -> baseDir.getAbsolutePath()
        )

      } catch {
        case e: ConfigException =>
          log.warn("Missing or invalid configuration. Cannot provide file base artifact repository. See error message for details.", e)
      }

    }
  }

}