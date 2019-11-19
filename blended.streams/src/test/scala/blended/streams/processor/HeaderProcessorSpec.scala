package blended.streams.processor

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HeaderProcessorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  System.setProperty("testName", "header")
  System.setProperty("Country", "cc")

  private[this] implicit val to : FiniteDuration = 3.seconds

  private val log = Logger[HeaderProcessorSpec]
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  private[this] val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)
  private[this] val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)
  private[this] val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private val msg = FlowMessage("Hallo Andreas")(FlowMessage.noProps)
  private val src = Source.single(FlowEnvelope(msg))
  private val sink = Sink.seq[FlowEnvelope]

  private val flow : (List[HeaderProcessorConfig], Option[ContainerIdentifierService]) => RunnableGraph[Future[Seq[FlowEnvelope]]] = (rules, idSvc) =>
    src.via(HeaderTransformProcessor(name = "t", log = envLogger, rules = rules, idSvc = idSvc).flow(envLogger)).toMat(sink)(Keep.right)

  private val result : (List[HeaderProcessorConfig], Option[ContainerIdentifierService]) => Seq[FlowEnvelope] = { (rules, idSvc) =>

    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
    implicit val materializer : Materializer = ActorMaterializer()

    Await.result(flow(rules, idSvc).run(), 3.seconds)
  }

  "The HeaderProcessor should" - {

    "set plain headers correctly" in {

      val r = result(List(
        HeaderProcessorConfig("foo", Some("bar"), overwrite = true)
      ), None)

      r should have size 1
      r.head.header[String]("foo") should be(Some("bar"))
    }

    "perform the normal resolution of container context properties" in {

      val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)

      idSvc.resolvePropertyString("$[[Country]]").get should be ("cc")

      val r = result(List(
        HeaderProcessorConfig("foo", Some("""$[[Country]]"""), overwrite = true),
        HeaderProcessorConfig("foo2", Some("""${{#foo}}"""), overwrite = true),
        HeaderProcessorConfig("test", Some("${{42}}"), overwrite = true)
      ), Some(idSvc))

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
