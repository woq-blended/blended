package blended.streams.processor

import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.util.logging.Logger

import scala.util.Success

case class HeaderTransformProcessor(
  name : String,
  rules : List[(String, String)],
  overwrite : Boolean = true,
  idSvc : Option[ContainerIdentifierService] = None
) extends FlowProcessor {

  private val log = Logger[HeaderTransformProcessor]

  override val f: IntegrationStep = { env =>

    try {
      val newMsg = rules.foldLeft(env.flowMessage){ case (c, (k,v)) =>
        val header = idSvc match {
          case None =>
            v
          case Some(s) =>
            val r = s.evaluatePropertyString(v.toString(), c.header.mapValues(_.value)).get
            r
        }
        c.withHeader(k,header, overwrite).get
      }
      log.debug(s"Header transformation complete [$name] : $newMsg")
      Success(Seq(env.copy(flowMessage = newMsg)))
    } catch {
      case t : Throwable =>
        Success(Seq(env.withException(t)))
    }
  }
}
