package blended.akka.http.restjms.internal

import java.io.File

import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.http.restjms.AkkaHttpRestJmsActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.IdAwareConnectionFactory
import blended.jmx.internal.BlendedJmxActivator
import blended.streams.internal.BlendedStreamsActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{AkkaHttpServerTestHelper, JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

abstract class AbstractJmsRequestorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with BeforeAndAfterAll
  with JmsConnectionHelper
  with AkkaHttpServerTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.restjms" -> new AkkaHttpRestJmsActivator()
  )

  private var responder : Option[JMSResponder] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val cf : IdAwareConnectionFactory = jmsConnectionFactory(registry, mustConnect = true, timeout = timeout).get
    responder = Some(new JMSResponder(cf, ctCtxt)(actorSystem))
    responder.foreach(_.start())
    Thread.sleep(2000)
  }

  override protected def afterAll(): Unit = {
    responder.foreach(_.stop())
  }
}
