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

package de.woq.osgi.akka.persistence {

import spray.json._
import akka.actor.{Terminated, ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.protocol.BundleActorStarted
import de.woq.osgi.akka.persistence.internal.PersistenceBundleName
import de.woq.osgi.akka.system.OSGIActor

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

    object DataObject {
      val LABEL       = "dataObject"
      val PROP_UUID   = "uuid"
      val PREFIX_TYPE = "type"
    }

    abstract class DataObject(uuid : String) {
      import DataObject._

      final def objectId = this.uuid
      def persistenceProperties : PersistenceProperties = (persistenceType, Map(PROP_UUID -> uuid))
      def persistenceType = getClass.getSimpleName
    }

    abstract class DataObjectFactory extends Actor with ActorLogging with PersistenceBundleName {
      this : OSGIActor =>

      def createObject(props: PersistenceProperties) : Option[DataObject]

      override def preStart(): Unit = {
        super.preStart()
        context.system.eventStream.subscribe(self, classOf[BundleActorStarted])
      }

      override def receive = registering

      def registering = LoggingReceive {
        case BundleActorStarted(name) if name == bundleSymbolicName => {
          setupFactory()
        }
      }

      def working = LoggingReceive {
        case CreateObjectFromProperties(props) => {
          createObject(props) match {
            case Some(dataObject) => {
              log.debug(s"Created data object [${dataObject.toString}].")
              sender ! QueryResult(List(dataObject))
            }
            case _ =>
          }
        }
        case Terminated(actor) => context.become(registering)
      }

      def setupFactory() {
        context.system.eventStream.subscribe(self, classOf[BundleActorStarted])

        (for(actor <- bundleActor(bundleSymbolicName).mapTo[ActorRef]) yield actor) map  {
          _ match {
            case actor : ActorRef => {
              log.debug("Registering data factory with persistence manager")
              actor ! RegisterDataFactory(self)
              context.watch(actor)
              context.become(working)
            }
            case dlq if dlq == context.system.deadLetters =>
          }
        }
      }
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

