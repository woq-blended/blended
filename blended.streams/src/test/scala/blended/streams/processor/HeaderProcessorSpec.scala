package blended.streams.processor

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HeaderProcessorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  System.setProperty("testName", "header")
  System.setProperty("Country", "cc")

  private val log = Logger[HeaderProcessorSpec]
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()


  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private val msg = FlowMessage("Hallo Andreas")(FlowMessage.noProps)
  private val src = Source.single(FlowEnvelope(msg))
  private val sink = Sink.seq[FlowEnvelope]

  private val flow : (List[HeaderProcessorConfig], Option[ContainerIdentifierService]) => RunnableGraph[Future[Seq[FlowEnvelope]]] = (rules, idSvc) =>
    src.via(HeaderTransformProcessor(name = "t", log = log, rules = rules, idSvc = idSvc).flow(log)).toMat(sink)(Keep.right)

  private val result : (List[HeaderProcessorConfig], Option[ContainerIdentifierService]) => Seq[FlowEnvelope] = { (rules, idSvc) =>

    implicit val timeout : FiniteDuration = 3.seconds
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
    implicit val materializer : Materializer = ActorMaterializer()

    Await.result(flow(rules, idSvc).run(), 3.seconds)
  }

  "The HeaderProcessor should" - {

    "set plain headers correctly" in {

      val parser = new SpelExpressionParser()
      val exp = parser.parseExpression("foo")
      val ctxt = new StandardEvaluationContext()

      val r = result(List(
        HeaderProcessorConfig("foo", Some("bar"), true)
      ), None)

      r should have size 1
      r.head.header[String]("foo") should be (Some("bar"))
    }

    "perform the normal resolution of container context properties" in {

      implicit val timeout = 3.seconds
      val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)

      idSvc.resolvePropertyString("$[[Country]]").get should be ("cc")

      val r = result(List(
        HeaderProcessorConfig("foo", Some("""$[[Country]]"""), true),
        HeaderProcessorConfig("foo2", Some("""${{#foo}}"""), true),
        HeaderProcessorConfig("test", Some("${{42}}"), true)
      ), Some(idSvc))

      log.info(r.toString())
      r.head.flowMessage.header should have size (3)
      r.head.header[String]("foo") should be (Some("cc"))
      r.head.header[String]("foo2") should be (Some("cc"))
      r.head.header[Int]("test") should be (Some(42))
    }
  }

}
