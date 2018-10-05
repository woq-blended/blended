package blended.streams.jms

import javax.jms.Message

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, StageLogging}
import blended.streams.message.{FlowMessage, MsgProperty}
import scala.collection.JavaConverters._

object JmsSourceStage {

  val jms2flowMessage : Flow[Message, FlowMessage, NotUsed] = {

    val f : Message => FlowMessage = { m : Message =>

      val props : Map[String, MsgProperty[_]] = m.getPropertyNames().asScala.map { name =>
        (name.toString, MsgProperty.lift(m.getObjectProperty(name.toString())).get)
      }.toMap

      FlowMessage(props, "test")
    }

    Flow.fromFunction(f)
  }
}

class JmsSourceStage extends GraphStage[SourceShape[Message]] {

  private val out = Outlet[Message]("JmsSource.out")

  override def shape: SourceShape[Message] = SourceShape[Message](out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {

    }
}
