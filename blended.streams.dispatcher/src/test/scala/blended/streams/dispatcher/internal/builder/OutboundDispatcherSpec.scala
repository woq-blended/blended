package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.stream.scaladsl.Flow
import blended.streams.FlowProcessor
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.util.Success

class OutboundDispatcherSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport  {

  override def country: String = "cc"
  override def location: String = "09999"
  override def loggerName: String = getClass().getName()
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  implicit val bs = new DispatcherBuilderSupport {
    override val prefix: String = "App"
    override val streamLogger: Logger = Logger(loggerName)
  }

  "The outbound flow of the dispatcher should" - {

    "produce a worklist completed event for successfull completions of the outbound flow" in {

      withDispatcherConfig { ctxt =>
        // a simple identity outbound flow for testing
        val outbound = Flow.fromGraph(FlowProcessor.fromFunction("out", bs.streamLogger){ env =>
          Success(env)
        })

        val g = DispatcherBuilder(ctxt.idSvc, ctxt.cfg).outbound(outbound)

        pending

      }

    }
  }

}
