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

package de.woq.blended.akka

import akka.actor.Actor
import com.typesafe.config.Config
import de.woq.blended.modules.FilterComponent
import org.osgi.framework.BundleContext

import scala.annotation.{StaticAnnotation, Annotation}
import scala.reflect.runtime.{universe => ru}

class ServiceDependency(filter: Option[FilterComponent] = None) extends StaticAnnotation

trait ActorWithDependencies extends InitializingActor { this :  BundleName =>

  def servicesAvailable : Receive = Actor.emptyBehavior

  def servicesUnavailable : Receive = Actor.emptyBehavior

  override def initialize(config: Config)(implicit bundleContext: BundleContext): Unit = {
    dependentFields
    self ! Initialized
  }

  override def working: Receive = Actor.emptyBehavior

  def dependentFields : Unit = {

    val mirror = ru.runtimeMirror(getClass.getClassLoader)
    val members = mirror.classSymbol(getClass).asType.typeSignature.members

    members.foreach { m => log.info(m.name.toString)
      m.annotations.foreach { a=>
        log.info(s"--${a.tpe.members}")
      }
    }
  }
}
