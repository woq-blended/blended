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

  package object protocol {

    type PersistenceProperties = Map[String, PersistenceProperty]

    implicit def primitive2Property(v: Any) : PersistenceProperty = v match {
      case b: Boolean  => BooleanProperty(b)
      case b: Byte     => ByteProperty(b)
      case s: Short    => ShortProperty(s)
      case i: Int      => IntProperty(i)
      case l: Long     => LongProperty(l)
      case f: Float    => FloatProperty(f)
      case d: Double   => DoubleProperty(d)
      case c: Char     => CharProperty(c)
      case s: String   => StringProperty(s)

      case x :: xs     => list2Property(x :: xs)
    }

    private[protocol] def property2Primitive(p: PersistenceProperty) : Any = p match {
      case BooleanProperty(b) => b
      case ByteProperty(b)    => b
      case ShortProperty(s)   => s
      case IntProperty(i)     => i
      case LongProperty(l)    => l
      case FloatProperty(f)   => f
      case DoubleProperty(d)  => d
      case CharProperty(c)    => c
      case StringProperty(s)  => s
      case ListProperty(l)    => l.map(property2Primitive(_))
    }

    private[protocol] def list2Property[T](l : List[T]) = ListProperty(l.map(primitive2Property(_)))

    def toQueryParams(props: PersistenceProperties) : Map[String, Any] =
      props.map { case (key: String, v: PersistenceProperty) => (key, property2Primitive(v)) }
  }

  package protocol {

    sealed class PersistenceProperty
    sealed case class BooleanProperty(b: Boolean) extends PersistenceProperty
    sealed case class ByteProperty(b: Byte) extends PersistenceProperty
    sealed case class ShortProperty(s: Short) extends PersistenceProperty
    sealed case class IntProperty(i: Int) extends PersistenceProperty
    sealed case class LongProperty(l: Long) extends PersistenceProperty
    sealed case class FloatProperty(f: Float) extends PersistenceProperty
    sealed case class DoubleProperty(d: Double) extends PersistenceProperty
    sealed case class CharProperty(c: Char) extends PersistenceProperty
    sealed case class StringProperty(s: String) extends PersistenceProperty
    sealed case class ListProperty[T <: PersistenceProperty](values: List[T]) extends PersistenceProperty

    object DataObject {
      val LABEL = "dataObject"
      val PROP_UUID = "uuid"
    }

    abstract class DataObject(uuid : String) {
      import DataObject._

      final def objectId = this.uuid
      def persistenceProperties : PersistenceProperties = Map(PROP_UUID -> uuid)
    }

    // Store a DataObject within in the persistence layer
    case class StoreObject(dataObject : DataObject)
  }
}

