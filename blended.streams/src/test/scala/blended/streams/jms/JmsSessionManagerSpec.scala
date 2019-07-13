package blended.streams.jms

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsSession}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms.Connection
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.util.Try

class JmsSessionManagerSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val timeout : FiniteDuration = 1.second
  private val cf : IdAwareConnectionFactory =
    mandatoryService[IdAwareConnectionFactory](registry)(None)

  "A JMS session manager should" - {

    "create a new session if it is called with session id that does not yet exist" in {

      val sessionsOpened : AtomicInteger = new AtomicInteger(0)

      val con : Connection = cf.createConnection()
      val mgr : JmsSessionManager = new JmsSessionManager(
        conn = con,
        maxSessions = 1
      ) {
        override def onSessionOpen: JmsSession => Try[Unit] = _ => Try { sessionsOpened.incrementAndGet() }
      }

      assert(mgr.getSession("foo").isSuccess)
      assert(sessionsOpened.get() == 1)
    }
  }
}
