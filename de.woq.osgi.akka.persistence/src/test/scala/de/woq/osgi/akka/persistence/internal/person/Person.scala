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

package de.woq.osgi.akka.persistence.internal.person

import de.woq.osgi.akka.persistence.protocol._
import java.util.UUID
import scala.collection.mutable
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.OSGIActor
import de.woq.osgi.akka.persistence.internal.PersistenceBundleName

object PersonFactory {

  import de.woq.osgi.akka.persistence.protocol._

  def create(props : PersistenceProperties) = {

    require(props._2.isDefinedAt("uuid"))
    require(props._2.isDefinedAt("name"))
    require(props._2.isDefinedAt("firstName"))

    val uuid : String = props._2("uuid").asInstanceOf[PersistenceProperty[String]].value

    new Person(
      uuid      = props._2("uuid").value.asInstanceOf[String],
      name      = props._2("name").value.asInstanceOf[String],
      firstName = props._2("firstName").value.asInstanceOf[String]
    )
  }
}

case class Person(uuid: String = UUID.randomUUID().toString, name: String, firstName: String) extends DataObject(uuid) {

  import spray.json._

  override def persistenceProperties: PersistenceProperties = {

    var builder =
      new mutable.MapBuilder[String, PersistenceProperty[_], mutable.Map[String, PersistenceProperty[_]]](mutable.Map.empty)

    val fields = this.toJson.asJsObject.fields
    fields.map { case (k: String, v: JsValue) =>
     (k, jsValue2Property(v))
    } foreach { case (k: String, v: PersistenceProperty[_]) => builder += (k -> v) }

    (super.persistenceProperties._1, builder.result().toMap)
  }
}

class PersonCreator extends DataObjectFactory with PersistenceBundleName with OSGIActor {

  override def createObject(props: PersistenceProperties): Option[DataObject] = props._1 match {
    case "Person" => Some(PersonFactory.create(props))
    case _ => None
  }
}


