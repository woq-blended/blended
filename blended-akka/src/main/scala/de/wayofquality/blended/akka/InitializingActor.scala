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

package de.wayofquality.blended.akka

import com.typesafe.config.Config
import de.wayofquality.blended.akka.protocol.{BundleActorInitialized, BundleActorState, InitializeBundle}
import org.osgi.framework.BundleContext

import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}

trait InitializingActor[T <: BundleActorState] extends OSGIActor { this: BundleName =>

  type CleanupState = T => Unit

  case class Initialized(state: T)

  def initialize(state : T) : Try[Initialized] = Success(Initialized(state))

  var cleanup : () => Unit = { () => {} }

  def working(state : T) : Receive

  def becomeWorking(state : T) : Unit = {
    context.become(working(state))
  }

  def createState(cfg: Config, bundleContext: BundleContext) : T

  def initializing[T](implicit tag : TypeTag[T]) : Receive = {

    case InitializeBundle(bc) =>
 
      log.info(s"Initializing bundle actor [$bundleSymbolicName]")
      val cfg = getConfig(bundleSymbolicName)
      val state = createState(cfg, bc)

      initialize(state) match {
        case Success(initialized) =>
          log.debug(s"Bundle actor [$bundleSymbolicName] initialized")
          context.system.eventStream.publish(BundleActorInitialized(bundleSymbolicName))
          becomeWorking(initialized.asInstanceOf[Initialized].state)
        case Failure(e) =>
          log.error(s"Error initializing bundle actor [$bundleSymbolicName].", e)
          context.stop(self)
      }
  }

  override def postStop(): Unit = {
    cleanup()
    super.postStop()
  }
}
