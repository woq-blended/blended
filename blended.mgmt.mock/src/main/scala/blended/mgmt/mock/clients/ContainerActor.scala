package blended.mgmt.mock.clients

import akka.actor.Props
import blended.container.context.api.ContainerIdentifierService
import blended.mgmt.agent.internal.MgmtReporter
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig
import blended.updater.config.ContainerInfo

import scala.util.Try

class ContainerActor(reporterConfig : MgmtReporterConfig, override val idSvc : ContainerIdentifierService) extends MgmtReporter {

  override val config : Try[MgmtReporter.MgmtReporterConfig] = Try(reporterConfig)
}

object ContainerActor {

  def props(reporterConfig : MgmtReporterConfig, idSvc : ContainerIdentifierService) : Props = Props(new ContainerActor(reporterConfig, idSvc))

}
