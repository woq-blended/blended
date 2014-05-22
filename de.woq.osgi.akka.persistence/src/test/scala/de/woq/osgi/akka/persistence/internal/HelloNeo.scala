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

package de.woq.osgi.akka.persistence.internal

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.{DynamicRelationshipType, GraphDatabaseService}

object HelloNeo {

  implicit val db = new GraphDatabaseFactory().newEmbeddedDatabase("target/test.neo4j")

  def withDb(f : GraphDatabaseService => Unit)(implicit db: GraphDatabaseService ) : Unit = {

    val tx = db.beginTx()

    try {
      f(db)
      tx.success()
    }
  }

  def main(args : Array[String]) {
    withDb { db : GraphDatabaseService =>
      val firstNode = db.createNode()
      firstNode.setProperty( "message", "Hello, " )
      val secondNode = db.createNode()
      secondNode.setProperty( "message", "World!" )

      val rel = firstNode.createRelationshipTo(secondNode, DynamicRelationshipType.withName("KNOWS"))
      rel.setProperty("message", "brave Neo4j")

      println(firstNode.getId)
      println(secondNode.getId)
    }

    db.shutdown()
  }

}
