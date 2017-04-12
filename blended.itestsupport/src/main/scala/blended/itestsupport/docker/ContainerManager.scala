package blended.itestsupport.docker

import akka.actor._
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.pattern._
import akka.util.Timeout
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Container, ContainerPort}
import com.typesafe.config.Config
import blended.itestsupport.{ContainerUnderTest, NamedContainerPort}
import blended.itestsupport.docker.protocol._

import scala.collection.convert.Wrappers.JListWrapper
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

private[docker] case class InternalMapDockerContainers(requestor: ActorRef, cuts: Map[String, ContainerUnderTest], client: DockerClient)
private[docker] case class InternalDockerContainersMapped(requestor: ActorRef, result : DockerResult[Map[String, ContainerUnderTest]])
private[docker] case class InternalStartContainers(cuts : Map[String, ContainerUnderTest])
private[docker] case class InternalContainersStarted(result : DockerResult[Map[String, ContainerUnderTest]])

trait DockerClientProvider {
  def getClient : DockerClient
}

class ContainerManager extends Actor with ActorLogging with Docker { this:  DockerClientProvider =>

  implicit val timeout = Timeout(30.seconds)
  implicit val eCtxt   = context.dispatcher
  val client : DockerClient = getClient  

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log
  
  def mapper = context.actorOf(Props(new DockerContainerMapper))
  
  def receive = LoggingReceive {
    
    case StartContainerManager(containers) => 
      
      val requestor = sender 
      
      val externalCt = config.getBoolean("docker.external")
      log.info(s"Containers have been started externally: [$externalCt]")

      val dockerHandler = context.actorOf(Props(new DockerContainerHandler()(client)), "DockerHandler")
      
      externalCt match {
        case true => self ! InternalContainersStarted(Right(configureDockerContainer(containers)))
        case _ => (dockerHandler ? InternalStartContainers(configureDockerContainer(containers))) pipeTo self
      }
      
      context.become(starting(dockerHandler, sender))
  }
  
  def starting(dockerHandler: ActorRef, requestor: ActorRef) : Receive = LoggingReceive {
    case r : InternalContainersStarted => r.result match {
      case Right(cuts) => mapper ! InternalMapDockerContainers(self, cuts, client)
      case _ => requestor ! r.result
    }  
    case r : InternalDockerContainersMapped => 
      log.info(s"Container Manager started with docker attached docker containers: [${r.result}]")
      requestor ! ContainerManagerStarted(r.result)
      r.result match {
        case Right(cuts) => context.become(running(dockerHandler, cuts))
        case _ => context.stop(self)
      }
  }
  
  def running(dockerHandler: ActorRef, cuts: Map[String, ContainerUnderTest]) : Receive = LoggingReceive {
    case gcd : GetContainerDirectory =>
      log.info(s"Getting container directory [${gcd.dir}] from [${gcd.containerId}]")
      dockerHandler.forward(gcd)

    case scm : StopContainerManager =>
      log.info("Stopping Test Container Manager")
      dockerHandler.forward(scm)
  }

  private[this] def configureDockerContainer(cut : Map[String, ContainerUnderTest]) : Map[String, ContainerUnderTest] = {
    val cuts = cut.values.map { ct =>
      search(searchByTag(ct.imgPattern)).zipWithIndex.map { case (img, idx) =>
        val ctName = s"${ct.ctName}_$idx"
        ct.copy(ctName = ctName, dockerName = s"${ctName}_${System.currentTimeMillis}", imgId = img.getId)
      }
    }.flatten
    
    cuts.map { ct => (ct.ctName, ct) }.toMap
  }
}

class DockerContainerMapper extends Actor with ActorLogging {
   
  def receive = LoggingReceive {
    case InternalMapDockerContainers(requestor, cuts, client) => 
      log.debug(s"Mapping docker containers $cuts")
      
      val mapped : Map[String, Either[Throwable, ContainerUnderTest]] = cuts.map { case (name, cut) =>
        dockerContainer(cut, client) match {
          case e if e.isEmpty => (name, Left(new Exception(s"No suitable docker container found for [${cut.ctName}]")))
          case head :: rest if rest.isEmpty => (name, Right(mapDockerContainer(head, cut)))
          case _ => (name, Left(new Exception(s"No unique docker container found for [${cut.ctName}]")))
        }
      }
      
      val errors = mapped.values.filter(_.isLeft).map(_.left.get.getMessage)
      val mappedCuts = mapped.values.filter(_.isRight).map(_.right.get)
      
      val result = errors match {
        case e if e.isEmpty => InternalDockerContainersMapped(requestor, Right(mappedCuts.map { c => (c.ctName, c) }.toMap ))
        case l => InternalDockerContainersMapped(requestor, Left(new Exception(errors.mkString(","))))
      }
      
      log.debug(s"$result")
      sender ! result
  }
  
  private[docker] def mapDockerContainer(dc: Container, cut: ContainerUnderTest) : ContainerUnderTest = {
    val mapped = cut.ports.map { case (name, port) => 
      (name, mapPort(dc.getPorts, port)) 
    }
    
    cut.copy(dockerName = rootName(dc), ports = mapped)    
  }
  
  private[docker] def rootName(dc : Container) : String = 
    dc.getNames.filter { _.indexOf("/", 1) == -1 }.head.substring(1)
  
    
  private[docker] def mapPort(dockerPorts: Array[ContainerPort], port: NamedContainerPort) : NamedContainerPort = {
    dockerPorts.filter { _.getPrivatePort() == port.privatePort }.toList match {
      case e if e.isEmpty => port
      case l => port.copy(publicPort = l.head.getPublicPort)
    }
  }
  
  private[docker] def dockerContainer(cut: ContainerUnderTest, client: DockerClient) : List[Container] = {
    
    val dc = JListWrapper(client.listContainersCmd().exec()).toList
    
    dockerContainerByName(cut, dc) match {
      case e if e.isEmpty => dockerContainerByImage(cut, dc)
      case l => l
    }
  } 
  
  private[docker] def dockerContainerByName(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by name: [${cut.dockerName}]")
    dc.filter(_.getNames.contains(s"/${cut.dockerName}"))
  }

  private[docker] def dockerContainerByImage(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by Image: [${cut.imgPattern}]")
    dc.filter(_.getImage.matches(cut.imgPattern))
  }
}

class DockerContainerHandler(implicit client: DockerClient) extends Actor with ActorLogging {

  implicit private[this] val timeout = Timeout(3.seconds)
  implicit private[this] val eCtxt = context.system.dispatcher
  
  def receive = LoggingReceive {
    case InternalStartContainers(cuts) =>
      log.info(s"Starting docker containers [$cuts]")
      
      val noDeps   = cuts.values.filter( _.links.isEmpty ).toList
      val withDeps = cuts.values.filter( _.links.nonEmpty).toList
      
      val pending  = withDeps.map { cut =>
        ( context.actorOf(Props(DependentContainerActor(cut))), cut )
      }

      noDeps.foreach{ startContainer }

      context.become(starting(sender, pending, noDeps, List.empty))
    case scm : StopContainerManager =>
      sender ! ContainerManagerStopped
      context.stop(self)
  }
  
  def starting(
    requestor           : ActorRef,
    pendingContainers   : List[(ActorRef, ContainerUnderTest)],
    startingContainers  : List[ContainerUnderTest],
    runningContainers   : List[ContainerUnderTest]
  ) : Receive = LoggingReceive {
    case ContainerStarted(result) => result match {
      case Right(cut) =>
        pendingContainers.foreach { _._1 ! ContainerStarted(Right(cut)) }
        val remaining = startingContainers.filter(_ != cut)
        val started = cut :: runningContainers

        if (pendingContainers.isEmpty && remaining.isEmpty) {
          log.info(s"Container Manager started [$started]")
          context.become(running(started))
          requestor ! InternalContainersStarted(Right(started.map { ct => (ct.ctName, ct) }.toMap ))
        } else {
          context.become(starting(requestor, pendingContainers, remaining, started))
        }
      case Left(e) => 
        log error s"Error in starting docker containers [${e.getMessage}]"
        requestor ! Left(e)
        context.stop(self)
    }
    case DependenciesStarted(result) => result match {
      case Right(cut) =>
        val pending  = pendingContainers.filter(_._1 != sender())
        startContainer(cut)
        context.become(starting(requestor, pending, cut :: startingContainers, runningContainers))
      case Left(e) => 
        context.stop(self)
    }
  }

  def running(managedContainers: List[ContainerUnderTest]) : Receive = LoggingReceive {
    case gcd: GetContainerDirectory =>
      log.info(s"Trying to get container diectory [${gcd.dir}] from [${gcd.containerId}]")
      val ctActor = context.actorSelection(gcd.containerId).resolveOne().map(_.forward(gcd))

    case scm : StopContainerManager =>
      
      log.info(s"Stopping Docker Container handler [$managedContainers]")
      
      implicit val timeout = new Timeout(scm.timeout)
      implicit val eCtxt = context.system.dispatcher
      val requestor = sender

      val stopFutures : Seq[Future[ContainerStopped]] = managedContainers.map { cut => 
        for{
          actor <- context.actorSelection(cut.ctName).resolveOne()
          stopped <- (actor ? StopContainer).mapTo[ContainerStopped]
        } yield stopped
      }
      
      val r = Await.result(Future.sequence(stopFutures), scm.timeout) 
      log.debug(s"Stopped Containers [$r]")
      requestor ! ContainerManagerStopped
      
      context.stop(self)
  }

  private[this] def startContainer(cut : ContainerUnderTest) : ActorRef = {

    val actor = context.actorOf(Props(ContainerActor(cut)), cut.ctName)
    actor ! StartContainer(cut.ctName)
    
    log.debug(s"Container Actor is [$actor]")
    
    actor
  }

}
