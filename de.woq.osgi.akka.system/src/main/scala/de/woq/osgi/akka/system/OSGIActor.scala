/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.osgi.akka.system

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import de.woq.osgi.akka.system.OSGIProtocol._
import akka.pattern.ask
import scala.concurrent.Future
import de.woq.osgi.akka.system.OSGIProtocol.GetService

trait OSGIActor { this : Actor =>

  implicit val timeout = new Timeout(1.second)
  implicit val ec = context.dispatcher

  def osgiFacade = context.actorSelection(s"/user/${WOQAkkaConstants.osgiFacadePath}").resolveOne()

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
