package blended.streams.processor

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.internal.BlendedAkkaActivator
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HeaderProcessorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  System.setProperty("testName", "header")
  System.setProperty("Country", "cc")

  private val log = Logger[HeaderProcessorSpec]
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()


  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private val msg = FlowMessage("Hallo Andreas")(FlowMessage.noProps)
  private val src = Source.single(FlowEnvelope(msg))
  private val sink = Sink.seq[FlowEnvelope]

  private val flow : (List[HeaderProcessorConfig]) => RunnableGraph[Future[Seq[FlowEnvelope]]] = rules => {
    src.via(
      HeaderTransformProcessor(name = "t", log = envLogger(log), rules = rules, ctCtxt = Some(ctCtxt)).flow(envLogger(log))
    )
    .toMat(sink)(Keep.right)
  }

  private val result : List[HeaderProcessorConfig] => Seq[FlowEnvelope] = { rules =>

    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)
    implicit val materializer : Materializer = ActorMaterializer()

    Await.result(flow(rules).run(), 3.seconds)
  }

  "The HeaderProcessor should" - {

    "set plain headers correctly" in {

      val r = result(List(
        HeaderProcessorConfig("foo", Some("bar"), overwrite = true)
      ))

      r should have size 1
      r.head.header[String]("foo") should be(Some("bar"))
    }

    "perform the normal resolution of container context properties" in {

      val r = result(List(
        HeaderProcessorConfig("foo", Some("""$[[Country]]"""), overwrite = true),
        HeaderProcessorConfig("foo2", Some(s"""$${{#foo}}"""), overwrite = true),
        HeaderProcessorConfig("test", Some(s"$${{42}}"), overwrite = true)
      ))

      // scalastyle:off magic.number
      log.info(r.toString())
      r.head.flowMessage.header should have size 3
      r.head.header[String]("foo") should be(Some("cc"))
      r.head.header[String]("foo2") should be(Some("cc"))
      r.head.header[Int]("test") should be(Some(42))
      // scalastyle:on magic.number
    }
  }

}
