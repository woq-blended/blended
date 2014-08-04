package de.woq.blended.akka.itest

import akka.util.Timeout
import akka.pattern.ask
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.condition.{SequentialComposedCondition, SequentialChecker}
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.jolokia.JolokiaAvailableCondition
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class BlendedDemoSpecSupport extends TestActorSys
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  val log = system.log

  "The demo container" should {

    "expose Jolokia as a REST service" in {

      val t = 30.seconds

      val url = Await.result(jolokiaUrl("blended_demo_0"), t)

      url should not be (None)
      assertCondition(new JolokiaAvailableCondition(url.get, t, Some("blended"), Some("blended")), t) should be (true)
    }
  }

  override protected def beforeAll() {
    startContainer(30.seconds) should be (ContainerManagerStarted)
  }

  override protected def afterAll(): Unit = super.afterAll()
}
