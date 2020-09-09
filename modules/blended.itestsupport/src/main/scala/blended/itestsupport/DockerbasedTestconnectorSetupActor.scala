package blended.itestsupport

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import blended.akka.MemoryStash
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.itestsupport.condition.{Condition, ConditionActor, ConditionProvider}
import blended.itestsupport.docker.{ContainerManagerActor}
import blended.itestsupport.docker.protocol._

/**
  * This class encapsulates the start sequence of all configured docker containers
  * in preparation of the integration test.
  *
  * The start sequence is kicked off with a StartContainerManager message, which
  * will initialize a docker container manager and hand over the configuration
  * of all ContainersUnderTest.
  *
  * The Actor will stay in "starting" state as long as not all containers have been
  * started within docker. Whenever a container is started successfully, the
  * corresponding ContainerUnderTest config is updated with the port mappings
  * of the exposed docker ports, so that they can be used to set up the required
  * connections for testing.
  *
  * Once all containers are started, the accumulated updated config is sent back
  * with a ContainerManagerStarted message. The accumulated config is then used
  * to call TestConnectorSetup.configure. It is in the developers responsibility
  * to setup all required connection information within the TestConnector required
  * to execute the tests.
  *
  * !!! Note : The TestConnector information is also used to execute the conditions
  *     checking if the containers are ready to go for the tests.
  */
class DockerbasedTestconnectorSetupActor
  extends Actor
  with ActorLogging
  with MemoryStash { this: TestConnectorSetup =>

  import DockerbasedTestconnectorSetupActor._

  def initializing: Receive = {
    case req: StartContainerManager =>
      log.debug("Starting container infractructure for test execution ...")
      val containerMgr = context.actorOf(ContainerManagerActor.props(), "ContainerMgr")

      // Kick off to start the containers
      containerMgr ! StartContainerManager(req.containerUnderTest)
      context.become(starting(List(sender()), containerMgr) orElse stashing)
  }

  // We will record all requestors having sent a StartDockerContainers request,
  // so that we can answer to them eventually
  def starting(requestors: List[ActorRef], containerMgr: ActorRef): Receive = {
    // All containers are started within docker, the result is the updated
    // config map and now contains the required information to connect to the
    // networkork services exposed by the docker containers
    case ContainerManagerStarted(result) =>
      result match {
        case Right(cuts) =>
          log.info("Configuring Test Connector ...")
          TestConnector.put("ctProxy", self)
          configure(cuts)
          context.become(working(cuts, containerMgr))
          // We will answer with a ConfiguredContainer message
          requestors.foreach(_ ! ConfiguredContainers(cuts))
        case m => requestors.foreach(_ ! m)
      }

    // if another requestor requests to start the docker infrastructure,
    // we will just record his interest
    case req: StartContainerManager =>
      context.become( starting( (sender() :: requestors).distinct , containerMgr) )
  }

  // finally in working state we will just respond to information requests as required
  def working(cuts: Map[String, ContainerUnderTest], containerMgr: ActorRef): Receive = {

    case _ : StartContainerManager => sender() ! ConfiguredContainers(cuts)

    // Once the docker containers are started within the infrastructure, a
    // ContainerReady_? is used to kick off the checking of all preconditions
    // that must be fulfilled before the actual tests can start. For example,
    // connections to databases or messaging providers must be established etc.
    // The Condition checks should use the configured TestConnector, so no
    // parameters are used for the containerReady() method.
    case ContainerReady_? =>
      implicit val eCtxt = context.system.dispatcher

      val requestor = sender()

      val condition = containerReady()

      log.info(s"Waiting for container condition(s) [$condition}]")

      val checker = context.system.actorOf(ConditionActor.props(condition))

      (checker ? CheckCondition)(condition.timeout).map {
        case cr: ConditionCheckResult => ContainerReady(
          cr.allSatisfied,
          cr.satisfied.map(_.description),
          cr.timedOut.map(c => s"Timed out: ${c.description}")
        )
        case _ => ContainerReady(false, List(), List())
      }.pipeTo(requestor)

    case ConfiguredContainers_? => sender() ! ConfiguredContainers(cuts)

    case cc: ConfiguredContainer_? => sender() ! ConfiguredContainer(cuts.get(cc.ctName))

    case gcd: GetContainerDirectory =>
      containerMgr.tell(gcd, sender())

    case wcd: WriteContainerDirectory =>
      containerMgr.tell(wcd, sender())

    case exec: ExecuteContainerCommand =>
      containerMgr.tell(exec, sender())

    case scm: StopContainerManager =>
      containerMgr.forward(scm)
  }

  /**
    * Check whether all preconditions to execute the tests are fulfilled.
    * @return A (possibly) combined condition that must be fulfilled.
    */
  def containerReady(): Condition = ConditionProvider.alwaysTrue()

  // We kick off in initializing state
  def receive = initializing orElse stashing
}

object DockerbasedTestconnectorSetupActor {

  case object ContainerReady_?
  case class ContainerReady(ready: Boolean, succeeded: List[String], failed: List[String])

  case object ConfiguredContainers_?
  case class ConfiguredContainers(cuts: Map[String, ContainerUnderTest])

  case class ConfiguredContainer_?(ctName: String)
  case class ConfiguredContainer(cut: Option[ContainerUnderTest])

}
