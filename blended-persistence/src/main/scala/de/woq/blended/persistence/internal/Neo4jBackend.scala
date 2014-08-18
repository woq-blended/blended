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

package de.woq.blended.persistence.internal

import org.neo4j.graphdb.{Node, DynamicLabel, GraphDatabaseService}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import com.typesafe.config.Config
import de.woq.blended.persistence.protocol._
import akka.event.LoggingAdapter
import java.io.File
import java.util.concurrent.TimeUnit
import org.neo4j.cypher.{ExecutionResult, ExecutionEngine}
import scala.collection.JavaConverters._
import scala.collection.mutable

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

      val path = try
        config.getString("dbPath")
      catch {
        case _ : Throwable => "neo4j.db"
      }
      s"$dir/$path"
    }
    case _ => ""
  }

  private[Neo4jBackend] def withDb[T](f: GraphDatabaseService => T) : T = {

    dbServiceRef match {
      case None => throw new Exception("Persistence backend not initialized properly.")
      case Some(db) => {
        val tx = db.beginTx()

        try {
          val result = f(db)
          tx.success()
          result
        } finally {
          tx.close()
        }
      }
    }
  }

  private[Neo4jBackend] def executeCypher(queries: QueryHolder*)(implicit log: LoggingAdapter) = {

    var result : Option[ExecutionResult] = None

    withDb[ExecutionResult] { db =>
      val engine = new ExecutionEngine(db)
      queries.foreach { qh =>
        val query = qh._1
        result = Some(qh._2 match {
          case None => {
            log.debug(s"Executing [$query]")
            engine.execute(query)
          }
          case Some(params) => {
            val realParams = params.mapValues(_.value)
            log.debug(s"Executing [$query] with [${realParams.toString}]")
            (engine.execute(query, realParams))
          }
        })
      }
      result.get
    }
  }

  override def initBackend(dir: String, config: Config)(implicit log: LoggingAdapter) {
    dbServiceRef match {
      case Some(ref) => throw new Exception("Backend already initialized.")
      case _ => {
        baseDir = Some(dir)
        dbConfig = Some(config)
        log.info(s"Initializing embedded Neo4j with path [$dbPath].")

        dbServiceRef = Some(new GraphDatabaseFactory().newEmbeddedDatabase(dbPath))

        withDb[Boolean] { db =>
          val constraints = db.schema().getConstraints(DynamicLabel.label(DataObject.LABEL))

          if (!constraints.iterator().hasNext) {
            db.schema()
              .constraintFor(DynamicLabel.label(DataObject.LABEL))
              .assertPropertyIsUnique(DataObject.PROP_UUID)
              .create()
          }
          true
        }

        withDb[Boolean] { db =>
          db.schema().awaitIndexesOnline(30, TimeUnit.SECONDS)
          true
        }

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

  override def store (obj: DataObject) (implicit log: LoggingAdapter) = withDb[Long] { db =>
    val node = executeCypher(
      (storeQuery(obj), Some(obj.persistenceProperties._2))
    )
    .next().values.toList.head.asInstanceOf[Node]
    log.debug(s"Saved object with nodeId [${node.getId}] and objectId [${obj.objectId}].")
    node.getId
  }

  override def get(uuid: String, objectType: String)(implicit log: LoggingAdapter): Option[PersistenceProperties] = withDb[Option[PersistenceProperties]] { db =>
    val nodes = executeCypher(
      (findByUuidQuery(uuid, objectType), None)
    )

    nodes.hasNext match {
      case false => {
        log.debug(s"No nodes in DB matching [$uuid].")
        None
      }
      case true => {
        val nodeList = nodes.next().toList
        if (nodeList.size > 1) log.warning(s"Found [${nodeList.size}] nodes with uuid [$uuid].")
        log.debug(s"Found [${nodeList.size}] nodes matching [$uuid].")
        val node = nodeList.head._2.asInstanceOf[Node]

        val objectType = getType(node) match {
          case None => throw new Exception(s"Could not determine type for object [$uuid].")
          case Some(s) => s
        }
        Some((objectType, getProperties(node)))
      }
    }
  }

  private[Neo4jBackend] def storeQuery(dataObject: DataObject) = {

    val params = (dataObject.persistenceProperties._2.keys.map { s : String => s"$s: {$s}" }).mkString(",")
    s"""
      MERGE (n: dataObject { uuid: "${dataObject.objectId}" })
        set n={$params}
        set n:${DataObject.PREFIX_TYPE}_${dataObject.persistenceProperties._1} RETURN n
    """
  }

  private[Neo4jBackend] def findByUuidQuery(uuid: String, objectType: String) = {
    s"""
      MATCH (n:dataObject:${DataObject.PREFIX_TYPE}_$objectType { uuid: "$uuid"}) RETURN n
    """
  }

  private[Neo4jBackend] def getType(node: Node) : Option[String] = {
    node.getLabels.asScala.toList.filter{
      l => l.name().startsWith(DataObject.PREFIX_TYPE)
    }.map{
      l => l.name().substring(DataObject.PREFIX_TYPE.length + 1)
    } match {
      case Nil => None
      case x :: xs => Some(x)
    }
  }

  private[Neo4jBackend] def getProperties(node: Node) : Map[String, PersistenceProperty[_]] = {

    var builder =
      new mutable.MapBuilder[String, PersistenceProperty[_], mutable.Map[String, PersistenceProperty[_]]](mutable.Map.empty)

    node.getPropertyKeys.asScala.foreach { k =>
      val v = node.getProperty(k)
      builder += (k -> object2Property(v))
    }

    builder.result().toMap
  }
}
