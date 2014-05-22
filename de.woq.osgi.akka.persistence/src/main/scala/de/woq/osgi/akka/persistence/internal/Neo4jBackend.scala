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

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import com.typesafe.config.Config
import de.woq.osgi.akka.persistence.protocol.DataObject
import akka.actor.ActorContext
import akka.event.LoggingAdapter

class Neo4jBackend extends PersistenceBackend {

  var dbServiceRef: Option[GraphDatabaseService] = None
  var dbConfig: Option[Config] = None
  var baseDir: Option[String] = None

  private[Neo4jBackend] def dbPath = dbConfig match {
    case Some(config) => {
      val dir = baseDir match {
        case Some(s) => s
        case _ => ""
      }
      val path = config.getString("dbPath")
      s"$dir/$path"
    }
    case _ => ""
  }

  private[Neo4jBackend] def withDb(f: GraphDatabaseService => Unit)(implicit db: GraphDatabaseService): Unit = {

    val tx = db.beginTx()

    try {
      f(db)
      tx.success()
    }
  }

  override def initBackend(dir: String, config: Config)(implicit log: LoggingAdapter) {
    dbServiceRef match {
      case Some(ref) => throw new Exception("Backend already initialized.")
      case _ => dbServiceRef = {
        baseDir = Some(dir)
        dbConfig = Some(config)
        log.info(s"Initializing embedded Neo4j with path [$dbPath].")
        Some(new GraphDatabaseFactory().newEmbeddedDatabase(s"$dbPath"))
      }
    }
  }

  override def shutdownBackend()(implicit log: LoggingAdapter) {
    dbServiceRef foreach {
      log.info(s"Shutting down embedded Neo4j for path [$dbPath].")
      _.shutdown()
    }

    dbServiceRef = None
    dbConfig = None
    baseDir = None
  }

  override def store (obj: DataObject) (implicit log: LoggingAdapter) = throw new UnsupportedOperationException ("Not yet ....")

}
