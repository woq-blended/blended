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

package de.wayofquality.blended.persistence.neo4j.internal.person

import de.wayofquality.blended.persistence.protocol._
import java.util.UUID
import scala.collection.mutable

object PersonFactory {

  import de.wayofquality.blended.persistence.protocol._

  def create(props : PersistenceProperties) = {

    require(props._2.isDefinedAt("uuid"))
    require(props._2.isDefinedAt("name"))
    require(props._2.isDefinedAt("firstName"))
    require(props._2.isDefinedAt("age"))

    new Person(
      uuid      = props._2("uuid").value.asInstanceOf[String],
      name      = props._2("name").value.asInstanceOf[String],
      firstName = props._2("firstName").value.asInstanceOf[String],
      age       = props._2("age").value.asInstanceOf[Int]
    )
  }
}

case class Person(
  uuid: String = UUID.randomUUID().toString, 
  name: String, 
  firstName: String,
  age: Int                 
) extends DataObject(uuid) {

  override def persistenceProperties: PersistenceProperties = {

    (super.persistenceProperties._1, Map(
      "uuid" -> uuid,
      "name" -> name,
      "firstName" -> firstName, 
      "age" -> age
    ))
  }
}

class PersonCreator extends DataObjectFactory {

  override def createObject(props: PersistenceProperties): Option[DataObject] = props._1 match {
    case cn if (cn.equals(classOf[Person].getName.replaceAll("\\.", "_"))) => Some(PersonFactory.create(props))
    case _ => None
  }
}


