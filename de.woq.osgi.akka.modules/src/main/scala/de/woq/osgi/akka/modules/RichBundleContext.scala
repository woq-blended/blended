/*
 * Copyright 2009-2011 Weigle Wilczek GmbH
 * Modifications 2014- Way of Quality UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.woq.osgi.akka.modules

import org.osgi.framework.{BundleContext, ServiceRegistration}

class RichBundleContext(context: BundleContext) {

  import scala.concurrent.ExecutionContext.Implicits.global

  assert(context != null, "The BundleContext must not be null!")

  /**
   * Creates a service, i.e. registers one with the OSGi service registry.
   * @param service The service to be registered; must not be null!
   * @param properties The service properties; must not be null!
   */
  def createService[S <: AnyRef] (
    service: S,
    properties: Props = Map.empty
  ) : ServiceRegistration[S] = {

    require(service != null, "The service object must not be null!")
    require(properties != null, "The service properties must not be null!")

    val interfaces : Array[String] = new InterfaceExtractor(service.getClass).interfaces map (c => c.getName )

    val serviceRegistration : ServiceRegistration[S] =
      (context.registerService(interfaces, service, if (properties.isEmpty) null else properties)).asInstanceOf[ServiceRegistration[S]]
    logger info s"Created service $service with interfaces ${interfaces.mkString("[", ",", "]")} and properties $properties."
    serviceRegistration
  }

  /**
   * Starting point for finding a service with the given service interface.
   * @param interface The service interface for which a ServiceFinder is to be created; must not be null!
   * @return A ServiceFinder for the given service interface
   */
  def findService[I <: AnyRef](interface: Class[I]): ServiceFinder[I] = {
    require(interface != null, "The service interface must not be null!")
    new ServiceFinder(interface, context)
  }

  /**
   * Starting point for finding all services with the given service interface.
   * @param interface The service interface for which a ServicesFinder is to be created; must not be null!
   * @return A ServiceFinders for the given service interface
   */
//  def findServices[I <: AnyRef](interface: Class[I]): ServicesFinder[I] = {
//    require(interface != null, "The service interface must not be null!")
//    new ServicesFinder(interface, context)
//  }

  /**
   * Starting point for watching services with the given service interface.
   * @param interface The service interface for which a ServicesWatcher is to be created; must not be null!
   * @return A ServicesWatcher for the given service interface
   */
//  def watchServices[I <: AnyRef](interface: Class[I]): ServicesWatcher[I] = {
//    require(interface != null, "The service interface must not be null!")
//    new ServicesWatcher(interface, context)
//  }
}
