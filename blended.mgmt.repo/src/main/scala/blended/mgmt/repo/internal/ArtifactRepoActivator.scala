package blended.mgmt.repo.internal

import java.io.File

import blended.domino.TypesafeConfigWatching
import blended.mgmt.repo.ArtifactRepo
import blended.mgmt.repo.file.FileArtifactRepo
import blended.util.logging.Logger
import com.typesafe.config.ConfigException
import domino.DominoActivator

class ArtifactRepoActivator() extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[ArtifactRepoActivator]

  whenBundleActive {
    log.info(s"About to activate bundle: ${bundleContext.getBundle().getSymbolicName()}")

    whenTypesafeConfigAvailable { (config, idService) =>
      log.debug(s"Config: ${config}")

      try {
        val repoId = config.getString("repoId")

        val baseDir = new File(config.getString("baseDir")).getAbsoluteFile()
        if (!baseDir.exists()) {
          log.warn(s"baseDir for artifact repository does not exists: ${baseDir}")
        }

        val repoService = new FileArtifactRepo(repoId, baseDir)
        log.info(s"Created service: ${repoService}")

        log.debug(s"About to register file based artifact repository to OSGi service registry: ${repoService}")
        repoService.providesService[ArtifactRepo](
          "repoId" -> repoId,
          "baseDir" -> baseDir.getAbsolutePath()
        )

      } catch {
        case e: ConfigException =>
          log.warn(e)("Missing or invalid configuration. Cannot provide file base artifact repository. See error message for details.")
      }

    }
  }

}