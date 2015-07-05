/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.activemq.brokerstarter

import java.io.File
import java.net.URI
import javax.jms.ConnectionFactory

import blended.akka.ConfigLocator
import blended.container.context.ContainerIdentifierService
import com.typesafe.config.{ConfigFactory, Config}
import domino.DominoActivator
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.slf4j.LoggerFactory
import org.springframework.jms.connection.CachingConnectionFactory

import scala.util.control.NonFatal

class BrokerActivator extends DominoActivator {

  whenBundleActive {

    val log = LoggerFactory.getLogger(classOf[BrokerActivator])
    whenServicePresent[ContainerIdentifierService] { idSvc =>

      var brokerService : Option[BrokerService] = None

      val cfgDir = idSvc.getContainerContext().getContainerConfigDirectory()

      val locator = new ConfigLocator(cfgDir){
        override protected def fallbackConfig: Config = ConfigFactory.parseFile(new File(cfgDir, "application.conf"))
      }

      val config = locator.getConfig(bundleContext.getBundle().getSymbolicName())

      val uri = s"file://$cfgDir/${config.getString("file")}"
      val provider = config.getString("provider")

      log.info("Configuring Active MQ Broker from config [{}] with provider id [{}].", uri, provider)

      val oldLoader = Thread.currentThread().getContextClassLoader()

      try {

        Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

        brokerService = Some(new XBeanBrokerFactory().createBroker(new URI(uri)))

        brokerService.foreach { broker =>
          broker.waitUntilStarted()

          val brokerName = broker.getBrokerName()

          log.info("ActiveMQ broker [{}] started successfully.", brokerName)

          val url = s"vm://$brokerName?create=false"
          val amqCF = new ActiveMQConnectionFactory(url)

          val cf = new CachingConnectionFactory(amqCF)

          cf.providesService[ConnectionFactory](Map(
            "provider" -> provider,
            "brokerName" -> brokerName
          ))
        }

      } catch {
        case  NonFatal(e) =>
          log.error("Failed to configure broker from [{}]", e, uri)
          throw e
      } finally {
        Thread.currentThread().setContextClassLoader(oldLoader)
      }

      onStop {
        brokerService.foreach { broker =>
          log.info("Stopping ActiveMQ Broker [{}]", broker.getBrokerName())
          broker.stop()
          broker.waitUntilStopped()
        }
        brokerService = None
      }
    }
  }
}
