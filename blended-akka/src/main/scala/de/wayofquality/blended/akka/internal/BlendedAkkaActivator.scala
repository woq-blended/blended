/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.wayofquality.blended.akka.internal

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.camel.CamelExtension
import akka.event.LogSource
import akka.osgi.ActorSystemActivator
import com.typesafe.config.{Config, ConfigFactory}
import de.wayofquality.blended.akka.BlendedAkkaConstants._
import de.wayofquality.blended.container.context.ContainerContext
import org.helgoboss.capsule.Capsule
import org.helgoboss.domino.DominoActivator
import org.osgi.framework.BundleContext

class BlendedAkkaActivator extends DominoActivator {
  whenBundleActive {
    addCapsule(new AkkaCapsule(this, bundleContext))
  }
}
  
private [internal] class AkkaCapsule(da: DominoActivator, osgiContext: BundleContext) extends ActorSystemActivator with Capsule {

  override def start(): Unit = start(osgiContext)

  override def stop(): Unit = stop(osgiContext)

  def configure(osgiContext: BundleContext, system: ActorSystem) : Unit = {
    val log = system.log

    log info "Registering Actor System as Service."
    registerService(osgiContext, system)

    log info "Creating Camel Akka Extension."
    val camel = CamelExtension(system)

    log info s"ActorSystem [${system.name}] initialized."
  }

  override def getActorSystemName(context: BundleContext): String = "BlendedActorSystem"

  override def getActorSystemConfiguration(context: BundleContext): Config = {
    ConfigFactory.parseFile(new File(configDir, "application.conf"))
  }

  private[AkkaCapsule] def configDir = {

    val defaultConfigDir = System.getProperty("karaf.home") + "/etc"
    
    da.withService[ContainerContext, String] {
      case Some(ctxt) => ctxt.getContainerConfigDirectory
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
