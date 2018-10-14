package blended.streams.processor

import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.util.logging.Logger

import scala.util.Success

case class HeaderTransformProcessor(
  name : String,
  log : Logger,
  rules : List[(String, Option[String], Boolean)],
  idSvc : Option[ContainerIdentifierService] = None
) extends FlowProcessor {

  override val f: IntegrationStep = { env =>

    log.debug(s"Processing rules [${rules.mkString(",")}]")

    try {
      val newMsg = rules.foldLeft(env.flowMessage){ case (c, (k,value,overwrite)) =>

        value match {
          case None =>
            c.removeHeader(k)
          case Some(v) =>
            val header = Option(idSvc match {
              case None =>
                v
              case Some(s) =>
                val props : Map[String, Any] = c.header.mapValues(_.value)
                s.resolvePropertyString(
                  v.toString(),
                  props + ("envelope" -> env)
                ).get
            })

            // Header might be null if a refernced property does not exist
            header match {
              case None =>
                log.warn(s"Header [$k] resolved to [null]")
                c
              case Some(v) =>
                log.debug(s"Processed Header [$k, $overwrite] : [$v]")
                c.withHeader(k, v, overwrite).get
            }
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
