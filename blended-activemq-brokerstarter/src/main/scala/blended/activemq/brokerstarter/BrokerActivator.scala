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

import java.net.URI
import javax.jms.ConnectionFactory

import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{BrokerFactory, BrokerService, DefaultBrokerFactory}
import org.apache.activemq.xbean.XBeanBrokerFactory
import org.springframework.jms.connection.CachingConnectionFactory

import scala.util.control.NonFatal

class BrokerActivator extends DominoActivator
  with TypesafeConfigWatching {

  whenBundleActive {
    whenTypesafeConfigAvailable { (config, idSvc) =>

      var brokerService: Option[BrokerService] = None

      val cfgDir = idSvc.getContainerContext().getContainerConfigDirectory()

      val brokerName = config.getString("brokerName")
      val uri = s"file://$cfgDir/${config.getString("file")}"
      val provider = config.getString("provider")

      log.info("Configuring Active MQ Broker from config [{}] with provider id [{}].", uri, provider)

      val oldLoader = Thread.currentThread().getContextClassLoader()

      try {

        BrokerFactory.setStartDefault(false)

        Thread.currentThread().setContextClassLoader(classOf[DefaultBrokerFactory].getClassLoader())

        brokerService = Some(new XBeanBrokerFactory().createBroker(new URI(uri)))

        brokerService.foreach { broker =>
          broker.setBrokerName(brokerName)
          broker.start()
          broker.waitUntilStarted()

          log.info(s"ActiveMQ broker [$brokerName] started successfully.")

          val url = s"vm://$brokerName?create=false"
          val amqCF = new ActiveMQConnectionFactory(url)

          val cf = new CachingConnectionFactory(amqCF)

          cf.providesService[ConnectionFactory](Map(
            "provider" -> provider,
            "brokerName" -> brokerName
          ))
        }

      } catch {
        case NonFatal(e) =>
          log.error("Failed to configure broker from [{}]", e, uri)
          throw e
      } finally {
        Thread.currentThread().setContextClassLoader(oldLoader)
      }

      onStop {
        brokerService.foreach { broker =>
          log.info(s"Stopping ActiveMQ Broker [${broker.getBrokerName()}]")
          broker.stop()
          broker.waitUntilStopped()
        }
        brokerService = None
      }
    }
  }
}
