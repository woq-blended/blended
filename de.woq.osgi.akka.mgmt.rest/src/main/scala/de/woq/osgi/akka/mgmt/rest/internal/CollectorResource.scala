/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.mgmt.rest.internal

import de.woq.osgi.java.jaxrs.JAXRSResource
import akka.actor.ActorSystem
import javax.ws.rs._
import javax.ws.rs.core.MediaType
import de.woq.osgi.java.mgmt_core.ContainerInfo
import akka.util.Timeout
import scala.concurrent.duration._

@Path("/container")
class CollectorResource(system: ActorSystem, bundleName : String) extends JAXRSResource {

import scala.concurrent.ExecutionContext.Implicits.global

  @GET
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getContainerInfos() = {
    Array(
      ContainerInfo("foo", Map())
    )
  }

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  def postContainerInfo(info : ContainerInfo) = {

    implicit val timeout = Timeout(1.second)

    system.actorSelection(s"/user/$bundleName").resolveOne().map { ref =>
      ref ! info
    }

    info
  }
}
