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

import org.neo4j.graphdb.{DynamicLabel, GraphDatabaseService}
import org.neo4j.graphdb.factory.{GraphDatabaseSettings, GraphDatabaseFactory}
import com.typesafe.config.Config
import de.woq.osgi.akka.persistence.protocol._
import akka.event.LoggingAdapter
import java.io.File
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit
import org.neo4j.cypher.ExecutionEngine

class Neo4jBackend extends PersistenceBackend {

  var dbServiceRef: Option[GraphDatabaseService] = None
  var dbConfig: Option[Config] = None
  var baseDir: Option[String] = None

  private[Neo4jBackend] def dbPath = dbConfig match {
    case Some(config) => {
      val dir = baseDir match {
        case Some(s) => new File(s).getAbsolutePath()
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
    } finally {
      tx.close()
    }
  }

  override def initBackend(dir: String, config: Config)(implicit log: LoggingAdapter) {
    dbServiceRef match {
      case Some(ref) => throw new Exception("Backend already initialized.")
      case _ => {
        baseDir = Some(dir)
        dbConfig = Some(config)
        log.info(s"Initializing embedded Neo4j with path [$dbPath].")

        implicit val db = new GraphDatabaseFactory().newEmbeddedDatabase(dbPath)

        withDb { db =>
          val constraints = db.schema().getConstraints(DynamicLabel.label(DataObject.LABEL))

          if (!constraints.iterator().hasNext) {
            db.schema()
              .constraintFor(DynamicLabel.label(DataObject.LABEL))
              .assertPropertyIsUnique(DataObject.PROP_UUID)
              .create()
          }
        }

        withDb { db =>
          db.schema().awaitIndexesOnline(30, TimeUnit.SECONDS)
        }

        dbServiceRef = Some(db)
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

  override def store (obj: DataObject) (implicit log: LoggingAdapter) = {
     dbServiceRef match {
      case None => throw new Exception("Backend is not initialized properly")
      case Some(db) => {
        implicit val backend = db
        withDb { db =>
          val engine = new ExecutionEngine(db)
          val query = createMergeQuery(obj)
          val params = toQueryParams(obj.persistenceProperties)
          log.info(query)
          log.info(params.toString())
          val resultIterator = engine.execute(query, params )
          val result = resultIterator.next()
          log.info(s"${result.toString}")
        }
        0
      }
    }
  }

  private def createMergeQuery(dataObject: DataObject) = {

    val params = (dataObject.persistenceProperties.keys.map { s : String => s"$s: {$s}" }).mkString(",")
    s"""MERGE (n: dataObject { uuid: "${dataObject.objectId}" }) set n={$params} RETURN n"""
  }

}
