package blended.akka.http.restjms.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.http.restjms.AkkaHttpRestJmsActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.IdAwareConnectionFactory
import blended.jmx.internal.BlendedJmxActivator
import blended.streams.internal.BlendedStreamsActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers

abstract class AbstractJmsRequestorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with BeforeAndAfterAll {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.restjms" -> new AkkaHttpRestJmsActivator()
  )

  protected val svcUrlBase : String = "http://localhost:9995/restjms"
  protected implicit val timeout : FiniteDuration = 3.seconds

  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer : ActorMaterializer = ActorMaterializer()

  protected val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)
  protected val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

  private val responder : JMSResponder = new JMSResponder(cf, ctCtxt)

  override protected def beforeAll(): Unit = {
    responder.start()
    Thread.sleep(2000)
  }

  override protected def afterAll(): Unit = {
    responder.stop()
  }
}
