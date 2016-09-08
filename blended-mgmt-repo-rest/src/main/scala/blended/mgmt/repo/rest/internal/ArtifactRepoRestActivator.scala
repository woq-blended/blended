package blended.mgmt.repo.rest.internal

import org.slf4j.LoggerFactory

import domino.DominoActivator
import blended.mgmt.repo.ArtifactRepo
import blended.akka.ActorSystemWatching
import domino.service_watching.ServiceWatcherEvent.AddingService
import domino.service_watching.ServiceWatcherEvent.ModifiedService
import domino.service_watching.ServiceWatcherEvent.RemovedService
import akka.actor.ActorRef
import org.osgi.service.http.HttpService
import blended.mgmt.repo.rest.ArtifactRepoServletActor

class ArtifactRepoRestActivator
    extends DominoActivator
    with ActorSystemWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRestActivator])

  whenBundleActive {
    log.info("About to activate bundle: {}", bundleContext.getBundle().getSymbolicName())

    whenServicePresent[HttpService] { httpSvc =>

      whenActorSystemAvailable { cfg =>

        var actors: Map[ArtifactRepo, ActorRef] = Map()

        /**
         * create and start actor and add to state
         */
        def addRepo(repo: ArtifactRepo): Unit = {
          val props = ArtifactRepoServletActor.props(cfg, repo)
          val actorRef = setupBundleActor(cfg, props)
          log.info("Created actor {} for artifact repo {}", Array[Object](actorRef, repo))
          actors += repo -> actorRef
          log.debug("known repos and their actors: {}", actors)
        }

        /**
         *  stop actor and remove from state
         */
        def removeRepo(repo: ArtifactRepo): Unit = {
          actors.get(repo).map { actor =>
            log.info("About to stop actor {} for artifact repo {}", Array(actor, repo))
            cfg.system.stop(actor)
            actors -= repo
          }
          log.debug("known repos and their actors: {}", actors)
        }

        watchServices[ArtifactRepo] {
          case AddingService(repo, context) =>
            addRepo(repo)
          case ModifiedService(repo, context) =>
            removeRepo(repo)
            addRepo(repo)
          case RemovedService(repo, context) =>
            removeRepo(repo)
        }

      }

    }
    // TODO: read config 

  }

}