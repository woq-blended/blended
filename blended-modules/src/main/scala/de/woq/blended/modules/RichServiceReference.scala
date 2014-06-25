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
package de.woq.blended.modules

import org.osgi.framework.{BundleContext, ServiceReference}
import org.slf4j.LoggerFactory

class RichServiceReference[I](serviceReference: ServiceReference[I]) {

  val logger = LoggerFactory.getLogger(getClass)

  assert(serviceReference != null, "The ServiceReference must not be null!")

  def invokeService[T](f: I => T)(implicit context: BundleContext) : Option[T] = {

    assert(f != null, "The function to be applied to the service must not be null!")
    assert(context != null, "The BundleContext must not be null!")

    try {
      context getService serviceReference match {
        case null => {
          logger debug s"Could not get service for ServiceReference [${serviceReference.toString}}]%s!"
          None
        }
        case service => {
          val result = Some(f(service.asInstanceOf[I]))
          logger debug s"Invocation of ServiceReference [${serviceReference.toString}] yields [${result}]"
          result
        }
      }
    } finally {
      context ungetService serviceReference
    }
  }

  /**
   * Gives access to service properties as Props (alias for Scala Map[String, Any]).
   * @return The service properties
   */
  lazy val properties: Props = Map(propsFrom(serviceReference) : _*)

  private def propsFrom(serviceReference: ServiceReference[I]): Array[(String, AnyRef)] = {
    serviceReference.getPropertyKeys match {
      case null => Array[(String, AnyRef)]()
      case keys => keys map { key => (key, serviceReference.getProperty(key)) }
    }
  }
}
