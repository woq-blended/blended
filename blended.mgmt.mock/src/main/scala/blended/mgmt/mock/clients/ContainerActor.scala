package blended.mgmt.mock.clients

import scala.util.Try

import akka.actor.Props
import blended.mgmt.agent.internal.MgmtReporter
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import blended.updater.config.ContainerInfo

class ContainerActor(reporterConfig: MgmtReporterConfig, containerInfo: ContainerInfo) extends MgmtReporter {
  import ContainerActor._

  override val config: Try[MgmtReporter.MgmtReporterConfig] = Try(reporterConfig)

  override def createContainerInfo: ContainerInfo = containerInfo.copy(timestampMsec = System.currentTimeMillis())

}

object ContainerActor {

  def props(reporterConfig: MgmtReporterConfig, containerInfo: ContainerInfo): Props = Props(new ContainerActor(reporterConfig, containerInfo))

}
