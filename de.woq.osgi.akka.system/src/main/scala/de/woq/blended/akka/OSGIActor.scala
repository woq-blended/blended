package de.woq.blended.akka

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import de.woq.blended.akka.protocol._

import scala.concurrent.duration._
import scala.concurrent.Future

/**
 * Created by andreas on 25/06/14.
 */
trait OSGIActor { this : Actor with ActorLogging =>

  implicit val timeout = new Timeout(1.second)
  implicit val ec = context.dispatcher

  def bundleActor(bundleName : String) = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne()
  }

  def osgiFacade = bundleActor(WOQAkkaConstants.osgiFacadePath)

  def getActorConfig(id: String) = for {
      facade <- osgiFacade.mapTo[ActorRef]
      config <- (facade ? ConfigLocatorRequest(id)).mapTo[ConfigLocatorResponse]
    } yield config

  def getServiceRef[I <: AnyRef](clazz : Class[I]) = {
    for {
      facade <- osgiFacade.mapTo[ActorRef]
      service <- (facade ? GetService(clazz)).mapTo[Service]
    } yield service
  }

  def invokeService[I <: AnyRef, T <: AnyRef](iface: Class[I])(f: InvocationType[I,T]) : Future[ServiceResult[Option[T]]] = {

    for {
      s <- getServiceRef[I](iface).mapTo[Service]
      r <- (s.service ? InvokeService(f)).mapTo[ServiceResult[Option[T]]]
    } yield (s,r) match {
      case (svc, result) => {
        svc.service ! UngetServiceReference
        result
      }
    }
  }
}
