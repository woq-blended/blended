package blended.streams.processor

import blended.container.context.api.ContainerContext
import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep
import blended.streams.message.FlowEnvelopeLogger
import blended.util.config.Implicits._
import blended.util.logging.LogLevel
import com.typesafe.config.Config

import scala.util.Try

object HeaderProcessorConfig {

  def create(cfg : Config) : HeaderProcessorConfig = {

    val name = cfg.getString("name")
    val expr = cfg.getStringOption("expression")
    val overwrite = cfg.getBoolean("overwrite", true)

    HeaderProcessorConfig(name, expr, overwrite)
  }
}

case class HeaderProcessorConfig(
  name : String,
  expression : Option[String],
  overwrite : Boolean
)

case class HeaderTransformProcessor(
  name : String,
  log : FlowEnvelopeLogger,
  rules : List[HeaderProcessorConfig],
  ctCtxt : Option[ContainerContext] = None
) extends FlowProcessor {

  override val f : IntegrationStep = { env =>

    Try {
      log.logEnv(env, LogLevel.Debug, s"Processing rules [${rules.mkString(",")}]")

      val newMsg = rules.foldLeft(env.flowMessage) {
        case (c, headerCfg) =>

          headerCfg.expression match {
            case None =>
              c.removeHeader(headerCfg.name)
            case Some(v) =>
              val header = Option(ctCtxt match {
                case None =>
                  v
                case Some(s) =>
                  val props : Map[String, Any] = c.header.mapValues(_.value).toMap
                  s.resolveString(
                    v.toString(),
                    props + ("envelope" -> env)
                  ).get
              })

            // Header might be null if a referenced property does not exist
            header match {
              case None =>
                log.logEnv(env, LogLevel.Warn, s"Header [${headerCfg.name}] resolved to [null]")
                c
              case Some(v) =>
                log.logEnv(env, LogLevel.Debug, s"Processed Header [${headerCfg.name}, ${headerCfg.overwrite}] : [$v]")
                c.withHeader(headerCfg.name, v, headerCfg.overwrite).get
            }
        }
      }
      log.logEnv(env, LogLevel.Debug, s"Header transformation complete [$name] : $newMsg")

      env.copy(flowMessage = newMsg)
    }
  }
}
