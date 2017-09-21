package blended.mgmt.mock.clients

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.slf4j.LoggerFactory

import akka.actor.ActorSystem
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import blended.mgmt.mock.MockObjects
import de.tototec.cmdoption.CmdlineParser

class MgmtMockClients(config: Config) {

  private[this] val log = LoggerFactory.getLogger(classOf[MgmtMockClients])

  implicit val system = ActorSystem("MgmtMockClients")

  def start(): Unit = {
    log.debug("About to start with config: {}", config)

    val reporterConfig = MgmtReporterConfig(
      registryUrl = config.url,
      updateIntervalMsec = config.updateIntervalMsec,
      initialUpdateDelayMsec = config.initialUpdateDelayMsec
    )

    val containerInfos = MockObjects.createContainer(config.clientCount)
    containerInfos.map { ci =>
      system.actorOf(ContainerActor.props(reporterConfig, ci), name = "container-" + ci.containerId)
    }
  }

  def stop(): Unit = {
    log.debug("About to stop")
    Await.ready(system.terminate(), Duration(10, TimeUnit.SECONDS))
  }

}

object MgmtMockClients {

  def main(args: Array[String]): Unit = {
    val cf = new ConfigFactory()
    val cp = new CmdlineParser(cf)
    cp.parse(args: _*)

    if (cf.showHelp) {
      cp.usage()
      return
    }

    val config = cf.get
    val app = new MgmtMockClients(config)

    app.start()

    Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook-app") {
      override def run(): Unit = {
        app.stop()
      }
    })

  }

}





