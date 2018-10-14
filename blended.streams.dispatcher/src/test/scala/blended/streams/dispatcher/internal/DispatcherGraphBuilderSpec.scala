package blended.streams.dispatcher.internal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers

class DispatcherGraphBuilderSpec extends TestKit(ActorSystem("dispatcher"))
  with LoggingFreeSpecLike
  with Matchers {

  private[this] val log = Logger[DispatcherGraphBuilderSpec]

  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val eCtxt = system.dispatcher

  "The Dispatcher RouteBuilder should" - {

    "split between normal and error messages" in {

      val good = FlowEnvelope(FlowMessage("Normal", FlowMessage.noProps))
      val bad = FlowEnvelope(FlowMessage("Error", FlowMessage.noProps)).withException(new Exception("Boom"))

      val result = DispatcherExecutor.execute(good, bad)

      result.out should be (List(good))
      result.error should be (List(bad))
      result.event should be (List.empty)
    }
  }
}
