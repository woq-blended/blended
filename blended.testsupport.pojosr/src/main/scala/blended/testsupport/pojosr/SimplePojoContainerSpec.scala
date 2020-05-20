package blended.testsupport.pojosr

import java.net.ServerSocket

import akka.actor.ActorSystem
import blended.container.context.api.ContainerContext
import blended.streams.FlowHeaderConfig
import blended.streams.message.FlowEnvelopeLogger
import blended.util.logging.Logger
import org.osgi.framework.Bundle
import org.scalatest.{BeforeAndAfterAll, TestSuite}

import scala.concurrent.duration._
import scala.reflect.ClassTag

abstract class SimplePojoContainerSpec
  extends TestSuite
  with BeforeAndAfterAll { this : PojoSrTestHelper =>

  def timeout : FiniteDuration = 5.seconds

  private var _registry : Option[BlendedPojoRegistry] = None

  def registry : BlendedPojoRegistry = _registry match {
    case Some(r) => r
    case None => throw new Exception("Pojo Registry not yet defined")
  }

  private var _ctCtxt : Option[ContainerContext] = None
  def ctCtxt : ContainerContext = _ctCtxt match {
    case Some(c) => c
    case None => throw new Exception("Container Context not yet available")
  }

  def headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)
  def envLogger : Logger => FlowEnvelopeLogger = log => FlowEnvelopeLogger.create(headerCfg, log)

  def actorSystem = mandatoryService[ActorSystem](registry)(ClassTag(classOf[ActorSystem]), timeout)

  /**
   * Specify, which properties are mandatory for the simulated container.
   */
  def mandatoryPropertyNames : List[String] = List.empty

  /**
   * If required, inject additional system properties when firing up the container.
   */
  def systemProperties : Map[String, String] = Map.empty

  def bundleByName(r : BlendedPojoRegistry)(name : String) : Option[Bundle] = r.getBundleContext().getBundles().find {
    b => b.getSymbolicName() == name
  }

  override protected def beforeAll(): Unit = {

    implicit val to : FiniteDuration = timeout

    super.beforeAll()
    val reg = createSimpleBlendedContainer(mandatoryPropertyNames, systemProperties).get
    bundles.foldLeft(reg) {
      case (current, (name, activator)) => startBundle(current)(name, activator).get._2
    }
    _registry = Some(reg)
    _ctCtxt = Some(mandatoryService[ContainerContext](registry))
  }

  override protected def afterAll() : Unit = {
    _registry.foreach(stopRegistry)
    super.afterAll()
  }
}
