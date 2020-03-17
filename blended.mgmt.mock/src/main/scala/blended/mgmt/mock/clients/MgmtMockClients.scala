package blended.mgmt.mock.clients

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import blended.container.context.api.ContainerContext
import blended.container.context.impl.internal.AbstractContainerContextImpl
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import blended.mgmt.mock.MockObjects
import blended.util.logging.Logger
import com.typesafe.config.{ConfigFactory, Config => TSConfig}
import de.tototec.cmdoption.CmdlineParser

import scala.beans.BeanProperty
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

class MgmtMockClients(config : Config) {

  private[this] val log = Logger[MgmtMockClients]
  private[this] val rnd = new Random()

  private[this] val ctCtxt : ContainerContext = new AbstractContainerContextImpl {
    @BeanProperty  val containerDirectory: String = "."
    @BeanProperty  override val containerConfigDirectory: String = containerDirectory
    @BeanProperty  override val containerLogDirectory: String = containerDirectory
    @BeanProperty  override val profileDirectory: String = containerDirectory
    @BeanProperty  override val profileConfigDirectory: String = containerDirectory
    @BeanProperty  override val containerHostname: String = "localhost"
    override val containerConfig: TSConfig = ConfigFactory.empty()
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



      system.actorOf(ContainerActor.props(reporterConfig, ctCtxt), name = "container-" + ci.containerId)
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

