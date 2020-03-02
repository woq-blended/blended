package blended.mgmt.mock.clients

import akka.actor.Props
import blended.container.context.api.ContainerContext
import blended.mgmt.agent.internal.MgmtReporter
import blended.mgmt.agent.internal.MgmtReporter.MgmtReporterConfig

import scala.util.Try

class ContainerActor(reporterConfig : MgmtReporterConfig, override val ctContext : ContainerContext) extends MgmtReporter {

  override val config : Try[MgmtReporter.MgmtReporterConfig] = Try(reporterConfig)
}

object ContainerActor {

  def props(reporterConfig : MgmtReporterConfig, ctContext : ContainerContext) : Props = Props(new ContainerActor(reporterConfig, ctContext))

}
