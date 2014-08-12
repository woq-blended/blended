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

package de.woq.blended.persistence {

import scala.language.implicitConversions
import akka.actor.ActorRef
import spray.json._

  package object protocol {

    // Persistence Properties consist of the type Marker and the key / value pair that make up the content of an object.
    // This type is the in memory representation of a persistable object
    type PersistenceProperties = (String, Map[String, PersistenceProperty[_]])

    // The QueryHolder is the tuple consisting og the Query to be executed as a String and the parameters that will be
    // used within the query as normal key / value pairs
    type QueryHolder = (String, Option[Map[String, PersistenceProperty[_]]])

    // helper function to turn jsValues as they are used within Spray into Persistence Properties
    implicit def jsValue2Property(v: JsValue) : PersistenceProperty[_] = v match {
      case JsBoolean(b)            => PersistenceProperty[Boolean](b)
      case JsString(s)             => PersistenceProperty[String](s)
      case JsNumber(d: BigDecimal) => PersistenceProperty[Double](d.toDouble)
    }

    // These definitions are jsut there to restrict the portential property types to primitives
    implicit def bool2Property(b: Boolean)  = PersistenceProperty[Boolean](b)
    implicit def byte2Property(b: Byte)     = PersistenceProperty[Byte](b)
    implicit def short2Property(s: Short)   = PersistenceProperty[Short](s)
    implicit def int2Property(i: Int)       = PersistenceProperty[Int](i)
    implicit def long2Property(l: Long)     = PersistenceProperty[Long](l)
    implicit def float2Property(f: Float)   = PersistenceProperty[Float](f)
    implicit def double2Property(d: Double) = PersistenceProperty[Double](d)
    implicit def char2Property(c: Char)     = PersistenceProperty[Char](c)
    implicit def string2Property(s: String) = PersistenceProperty[String](s)

    implicit def object2Property(obj: Any) : PersistenceProperty[_] = obj match {
      case b: Boolean      => bool2Property(b)
      case b: Byte         => byte2Property(b: Byte)
      case s: Short        => short2Property(s)
      case i: Int          => int2Property(i)
      case l: Long         => long2Property(l)
      case f: Float        => float2Property(f)
      case d: Double       => double2Property(d)
      case c: Char         => char2Property(c)
      case s: String       => string2Property(s)
      case o => throw new Exception(s"Unsupported property type [${o.getClass.getName}]")
    }

    // Helper function to turn persistence properties back into the primitive
    implicit def property2Primitive[T](p: PersistenceProperty[T]) : T = p.value
  }

  package protocol {

    sealed case class PersistenceProperty[T](v : T) {
      require(
        v.isInstanceOf[Boolean] || v.isInstanceOf[Byte] || v.isInstanceOf[Short] ||
        v.isInstanceOf[Int]     || v.isInstanceOf[Long] || v.isInstanceOf[Float] ||
        v.isInstanceOf[Double]  || v.isInstanceOf[Char] || v.isInstanceOf[String]
      )
      def value : T = v
    }

    // DataObjects are persistable via the persistence manager. Usually they support JSON marshalling / demarshalling
    // as well, but that is only required if they are used within a REST interface.
    object DataObject {
      val LABEL       = "dataObject"
      val PROP_UUID   = "uuid"
      val PREFIX_TYPE = "type"
    }

    // A data object is uniquely identified gy its unique id. This id is a technial id and used within ther persistence
    // layer as a unique index. To leverage the persistence manager, a data object must know how create the
    // PersistenceProperties from its internal data. This can usually done by a simple tranformation of the JSONObject.
    abstract class DataObject(uuid : String) {
      import de.woq.blended.persistence.protocol.DataObject._

      // The object unique id
      final def objectId = this.uuid

      // Create the PersistenceProperties from the object content. This consists of the type tag and a map of the actual
      // properties. The type tag is used to determine which data object factory is capable of creating a properly
      // typed object from a given PersistenceProperties instance.
      def persistenceProperties : PersistenceProperties = (persistenceType, Map(PROP_UUID -> uuid))
      def persistenceType = getClass.getSimpleName
    }



    // Store a DataObject within in the persistence layer
    case class StoreObject(dataObject : DataObject)
    case class QueryResult(result: List[DataObject])

    // Find an object by its unique Id and the objectType
    case class FindObjectByID(uuid: String, objectType: String)

    // The persistence manager will use delegates to create a properly typed DataObject from it's own persistence properties
    case class RegisterDataFactory(factory: ActorRef)
    case class CreateObjectFromProperties(props: PersistenceProperties)
    case class ObjectCreated(dataObject: DataObject)
  }
}

