package blended.streams.processor

import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.util.logging.Logger

import scala.util.Success

case class HeaderTransformProcessor(
  name : String,
  rules : List[(String, Option[String], Boolean)],
  idSvc : Option[ContainerIdentifierService] = None
) extends FlowProcessor {

  private val log = Logger[HeaderTransformProcessor]

  override val f: IntegrationStep = { env =>

    log.debug(s"Processing rules [${rules.mkString(",")}]")

    try {
      val newMsg = rules.foldLeft(env.flowMessage){ case (c, (k,value,overwrite)) =>

        value match {
          case None =>
            c.removeHeader(k)
          case Some(v) =>
            val header = idSvc match {
              case None =>
                v
              case Some(s) =>
                val props : Map[String, Any] = c.header.mapValues(_.value)
                s.resolvePropertyString(
                  v.toString(),
                  props + ("envelope" -> env)
                ).get
            }
            log.debug(s"Processed Header [$k, $overwrite] : [$header]")
            c.withHeader(k, header, overwrite).get
        }

      }
      log.debug(s"Header transformation complete [$name] : $newMsg")
      Success(Seq(env.copy(flowMessage = newMsg)))
    } catch {
      case t : Throwable =>
        Success(Seq(env.withException(t)))
    }
  }
}
