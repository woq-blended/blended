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

package de.woq.blended.akka.internal

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.event.LogSource
import akka.osgi.ActorSystemActivator
import com.typesafe.config.{Config, ConfigFactory}
import de.woq.blended.akka.BlendedAkkaConstants
import de.woq.blended.container.context.ContainerContext
import de.woq.blended.modules._
import BlendedAkkaConstants._
import org.osgi.framework.BundleContext

class BlendedAkkaActivator extends ActorSystemActivator {

  def configure(osgiContext: BundleContext, system: ActorSystem) {
    val log = system.log

    log info "Creating Akka OSGi Facade"
    system.actorOf(Props(OSGIFacade()(osgiContext)), osgiFacadePath)

    log info "Registering Actor System as Service."
    registerService(osgiContext, system)

    log info s"ActorSystem [${system.name}] initialized."
  }

  override def getActorSystemName(context: BundleContext): String = "BlendedActorSystem"

  override def getActorSystemConfiguration(context: BundleContext): Config = {
    ConfigFactory.parseFile(new File(configDir(context), "application.conf"))
  }
  
  private[BlendedAkkaActivator] def configDir(implicit osgiContext : BundleContext) = {

    val defaultConfigDir = System.getProperty("karaf.home") + "/etc"

    (osgiContext findService(classOf[ContainerContext])) match {
      case Some(svcRef) => svcRef invokeService { ctx => ctx.getContainerConfigDirectory } match {
        case Some(s)  => s
        case _ => defaultConfigDir
      }
      case _ => defaultConfigDir
    }
  }
}

object BlendedAkkaActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}