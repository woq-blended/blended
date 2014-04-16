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
package de.woq.osgi.akka

import java.util.Dictionary
import org.osgi.framework.{ BundleContext, ServiceReference }
import org.slf4j.LoggerFactory

/**
 * Some implicit conversions and other stuff essential for the ScalaModules DSL.
 */
package object modules {

  /**
   * Type alias for service properties.
   */
  type Props = Map[String, Any]

  /**
   * Implicitly converts a BundleContext into a RichBundleContext.
   * @param context The BundleContext to be converted; must not be null!
   * @return The RichBundleContext initialized with the given BundleContext
   */
  implicit def toRichBundleContext(context: BundleContext): RichBundleContext = {

    require(context != null, "The BundleContext must not be null!")
    new RichBundleContext(context)
  }

  /**
   * Implicitly converts a ServiceReference into a RichServiceReference.
   * @param serviceReference The ServiceReference to be converted; must not be null!
   * @return The RichServiceReference initialized with the given ServiceReference
   */
  implicit def toRichServiceReference[I](serviceReference: ServiceReference[I]) : RichServiceReference[I] = {
    require(serviceReference != null, "The ServiceReference must not be null!")
    new RichServiceReference[I](serviceReference)
  }

  /**
   * Implicitly converts a Pair into a Map in order to easily define single entry service properties.
   * @param pair The pair to be converted
   * @return A Map initialized with the given pair or null, if the given pair is null
   */
  implicit def pairToMap[A, B](pair: (A, B)): Map[A, B] =
    if (pair == null) null else Map(pair)

  /**
   * Implicitly converts a String attribute into a SimpleOpBuilder FilterComponent.
   * @param attr The attribute to be converted; must not be null!
   * @return A SimpleOpBuilder initialized with the given String attribute
   */
  implicit def toSimpleOpBuilder(attr: String): SimpleOpBuilder = {
    require(attr != null, "The attr must not be null!")
    new SimpleOpBuilder(attr)
  }

  /**
   * Implicitly converts a String attribute into a PresentBuilder FilterComponent.
   * @param attr The attribute to be converted; must not be null!
   * @return A PresentBuilder initialized with the given String attribute
   */
  implicit def toPresentBuilder(attr: String): PresentBuilder = {
    require(attr != null, "The attr must not be null!")
    new PresentBuilder(attr)
  }

  private[modules] implicit def scalaMapToJavaDictionary[K, V](map: Map[K, V]) = {
    import scala.collection.JavaConversions._
    if (map == null) null: Dictionary[K, V]
    else new Dictionary[K, V] {
      override def size = map.size
      override def isEmpty = map.isEmpty
      override def keys = map.keysIterator
      override def elements = map.valuesIterator
      override def get(o: Object) = map.get(o.asInstanceOf[K]) match {
        case None => null.asInstanceOf[V]
        case Some(value) => value.asInstanceOf[V]
      }
      override def put(key: K, value: V) =
        throw new UnsupportedOperationException("This Dictionary is read-only!")
      override def remove(o: Object) =
        throw new UnsupportedOperationException("This Dictionary is read-only!")
    }
  }
}
