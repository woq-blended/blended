package blended.mgmt.mock.clients

import akka.actor.ActorSystem
import blended.mgmt.mock.MockObjects
import de.tototec.cmdoption.CmdOption
import blended.updater.config.ContainerInfo
import akka.actor.Actor
import akka.actor.Props
import blended.mgmt.agent.internal.MgmtReporter
import scala.util.Try
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import de.tototec.cmdoption.CmdlineParser
import org.slf4j.LoggerFactory

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

class ContainerActor(reporterConfig: MgmtReporterConfig, containerInfo: ContainerInfo) extends MgmtReporter {
  import ContainerActor._

  override val config: Try[MgmtReporter.MgmtReporterConfig] = Try(reporterConfig)

  override def createContainerInfo: ContainerInfo = containerInfo.copy(timestampMsec = System.currentTimeMillis())

}

object ContainerActor {

  def props(reporterConfig: MgmtReporterConfig, containerInfo: ContainerInfo): Props = Props(new ContainerActor(reporterConfig, containerInfo))

}

case class Config(
    clientCount: Int = 1000,
    url: String = "http://mgmt:9191/mgmt/container",
    updateIntervalMsec: Long = 20000,
    initialUpdateDelayMsec: Long = 2000) {

  override def toString(): String = getClass().getSimpleName() +
    "(clientCount=" + clientCount +
    ",url=" + url +
    ",updateIntervalMsec=" + updateIntervalMsec +
    ",initialUpdateDelayMsec" + initialUpdateDelayMsec +
    ")"
}

class ConfigFactory {

  private var config = Config()

  def get: Config = config

  @CmdOption(names = Array("--client-count", "-c"), args = Array("n"), description = "The number of clients to generate")
  def clientCount(count: Int): Unit =
    config = config.copy(clientCount = count)

  @CmdOption(names = Array("--url", "-u"), args = Array("url"), description = "The URL of the managment server")
  def url(url: String): Unit =
    config = config.copy(url = url)

  @CmdOption(names = Array("--help", "-h"), description = "Print this help")
  var showHelp: Boolean = false

}

