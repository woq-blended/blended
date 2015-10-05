/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.akka.internal

import java.io.File

import akka.actor.ActorSystem
import akka.event.LogSource
import akka.osgi.ActorSystemActivator
import com.typesafe.config.{Config, ConfigFactory}
import blended.container.context.{ContainerIdentifierService, ContainerContext}
import domino.capsule.Capsule
import domino.DominoActivator
import org.osgi.framework.BundleContext

object BlendedAkkaActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}

class BlendedAkkaActivator extends DominoActivator {

  private class AkkaCapsule(bundleContext: BundleContext, containerContext: ContainerContext)
    extends ActorSystemActivator with Capsule {

    override def start(): Unit = start(bundleContext)

    override def stop(): Unit = stop(bundleContext)

    def configure(osgiContext: BundleContext, system: ActorSystem) : Unit = {
      val log = system.log

      log info "Registering Actor System as Service."
      registerService(osgiContext, system)

      log info s"ActorSystem [${system.name}] initialized."
    }

    override def getActorSystemName(context: BundleContext): String = "BlendedActorSystem"

    override def getActorSystemConfiguration(context: BundleContext): Config = {

      val sysProps = ConfigFactory.systemProperties()
      val envProps = ConfigFactory.systemEnvironment()

      ConfigFactory.parseFile(
        new File(containerContext.getContainerConfigDirectory, "application.conf")
      ).withFallback(sysProps).withFallback(envProps).resolve()
    }
  }

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { svc =>
      addCapsule(new AkkaCapsule(bundleContext, svc.getContainerContext()))
    }
  }
}
  
