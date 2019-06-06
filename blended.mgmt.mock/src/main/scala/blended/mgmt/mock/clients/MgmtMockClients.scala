package blended.mgmt.mock.clients

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import blended.mgmt.mock.MockObjects
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import blended.util.logging.Logger
import com.typesafe.config.{ConfigFactory, Config => TSConfig}
import de.tototec.cmdoption.CmdlineParser

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

class MgmtMockClients(config : Config) {

  private[this] val log = Logger[MgmtMockClients]
  private[this] val rnd = new Random()

  private[this] val ctCtxt : ContainerContext = new ContainerContext {
    override def getContainerDirectory() : String = "."
    override def getContainerConfigDirectory() : String = getContainerDirectory()
    override def getContainerLogDirectory() : String = getContainerDirectory()
    override def getProfileDirectory() : String = getContainerDirectory()
    override def getProfileConfigDirectory() : String = getContainerDirectory()
    override def getContainerHostname() : String = "localhost"
    override def getContainerCryptoSupport() : ContainerCryptoSupport = BlendedCryptoSupport.initCryptoSupport("pwd.txt")
    override def getContainerConfig() : TSConfig = ConfigFactory.empty()
  }

  implicit val system : ActorSystem = ActorSystem("MgmtMockClients")

  def start() : Unit = {
    log.debug(s"About to start with config: $config")

    val containerInfos = MockObjects.createContainer(config.clientCount)

    containerInfos.map { ci =>

      val diff = Math.abs(config.updateIntervalMsecMax - config.updateIntervalMsecMin)
      val interval = (Math.abs(rnd.nextLong()) % diff) + Math.min(config.updateIntervalMsecMax, config.updateIntervalMsecMin)

      val reporterConfig = MgmtReporterConfig(
        registryUrl = config.url,
        updateIntervalMsec = interval,
        initialUpdateDelayMsec = config.initialUpdateDelayMsec
      )

      val idSvc : ContainerIdentifierService = new ContainerIdentifierServiceImpl(ctCtxt) {
        override lazy val uuid : String = ci.containerId
      }

      system.actorOf(ContainerActor.props(reporterConfig, idSvc), name = "container-" + ci.containerId)
    }
  }

  def stop() : Unit = {
    log.debug("About to stop")
    // scalastyle:off magic.number
    Await.ready(system.terminate(), Duration(10, TimeUnit.SECONDS))
    // scalastyle:on magic.number
  }

}

object MgmtMockClients {

  def main(args : Array[String]) : Unit = {
    val cf = new Config.Factory()
    val cp = new CmdlineParser(cf)
    cp.parse(args : _*)

    if (cf.showHelp) {
      cp.usage()
      System.exit(1)
    }

    val config = cf.get
    val app = new MgmtMockClients(config)

    app.start()

    Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook-app") {
      override def run() : Unit = {
        app.stop()
      }
    })

  }

}

