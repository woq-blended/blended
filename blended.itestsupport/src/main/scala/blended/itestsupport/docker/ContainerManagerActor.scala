package blended.itestsupport.docker

import akka.actor._
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.pattern._
import akka.util.Timeout
import com.github.dockerjava.api.DockerClient
import com.typesafe.config.Config
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.docker.protocol._

import scala.concurrent.duration._

private[docker] case class InternalMapDockerContainers(requestor: ActorRef, cuts: Map[String, ContainerUnderTest], client: DockerClient)

private[docker] case class InternalDockerContainersMapped(requestor: ActorRef, result: DockerResult[Map[String, ContainerUnderTest]])

private[docker] case class InternalStartContainers(cuts: Map[String, ContainerUnderTest])

private[docker] case class InternalContainersStarted(result: DockerResult[Map[String, ContainerUnderTest]])


class ContainerManagerActor extends Actor with ActorLogging with Docker {
  this: DockerClientProvider =>

  implicit val timeout = Timeout(60.seconds)
  implicit val eCtxt = context.dispatcher
  val client: DockerClient = getClient

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log

  def mapper = context.actorOf(Props(new DockerContainerMapperActor))

  def receive = LoggingReceive {

    case StartContainerManager(containers) =>

      val requestor = sender()

      val externalCt = config.getBoolean("docker.external")
      log.info(s"Containers have been started externally: [$externalCt]")

      val dockerHandler = context.actorOf(DockerContainerHandler.props(client), "DockerHandler")

      externalCt match {
        case true => self ! InternalContainersStarted(Right(configureDockerContainer(containers)))
        case _ => (dockerHandler ? InternalStartContainers(configureDockerContainer(containers))) pipeTo self
      }

      context.become(starting(dockerHandler, requestor))
  }

  def starting(dockerHandler: ActorRef, requestor: ActorRef): Receive = LoggingReceive {
    case r: InternalContainersStarted => r.result match {
      case Right(cuts) => mapper ! InternalMapDockerContainers(self, cuts, client)
      case _ => requestor ! r.result
    }
    case r: InternalDockerContainersMapped =>
      log.info(s"Container Manager started with docker attached docker containers: [${r.result}]")
      requestor ! ContainerManagerStarted(r.result)
      r.result match {
        case Right(cuts) => context.become(running(dockerHandler, cuts))
        case _ => context.stop(self)
      }
  }

  def running(dockerHandler: ActorRef, cuts: Map[String, ContainerUnderTest]): Receive = LoggingReceive {
    case gcd: GetContainerDirectory =>
      dockerHandler.tell(gcd, sender())

    case wcd: WriteContainerDirectory =>
      dockerHandler.tell(wcd, sender())

    case exec: ExecuteContainerCommand =>
      dockerHandler.tell(exec, sender())

    case scm: StopContainerManager =>
      log.info("Stopping Test Container Manager")
      dockerHandler.forward(scm)
  }

  private[this] def configureDockerContainer(cut: Map[String, ContainerUnderTest]): Map[String, ContainerUnderTest] = {
    val cuts = cut.values.flatMap { ct =>

      val searchImg = searchByTag(ct.imgPattern)
      val found = search(searchImg)

      found.zipWithIndex.map { case (img, idx) =>
        val ctName = s"${ct.ctName}_$idx"
        ct.copy(ctName = ctName, dockerName = s"${ctName}_${System.currentTimeMillis}", imgId = img.getId)
      }
    }

    cuts.map { ct => (ct.ctName, ct) }.toMap
  }
}


object ContainerManagerActor {

  def props(dockerClientProvider: DockerClientProvider): Props = Props(new ContainerManagerActor with DockerClientProvider {
    override def getClient: DockerClient = dockerClientProvider.getClient
  })

  def props(): Props = Props(new ContainerManagerActor with DockerClientProvider {
    override def getClient: DockerClient = {
      implicit val logger = context.system.log
      DockerClientFactory(context.system.settings.config)
    }
  })

  sealed trait Protocol


}
