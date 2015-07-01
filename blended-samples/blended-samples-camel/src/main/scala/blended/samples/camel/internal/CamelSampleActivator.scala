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

package blended.samples.camel.internal

import javax.jms.ConnectionFactory

import domino.DominoActivator
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.impl.DefaultCamelContext
import org.slf4j.LoggerFactory

class CamelSampleActivator extends DominoActivator {

  whenBundleActive {

    val log = LoggerFactory.getLogger(classOf[CamelSampleActivator])
    whenAdvancedServicePresent[ConnectionFactory]("(provider=activemq)") { cf =>

      val ctxt = new DefaultCamelContext()
      ctxt.setName("SampleContext")
      ctxt.addComponent("activemq", JmsComponent.jmsComponent(cf))

      ctxt.addRoutes(new RouteBuilder() {
        override def configure(): Unit = {
          from("activemq:queue:SampleIn").id("SampleRoute")
            .setHeader("Description", constant("BlendedSample"))
            .to("activemq:queue:SampleOut")
        }
      })

      ctxt.start()

      onStop {
        log.debug("Stopping Camel Context")
        ctxt.stop()
      }
    }
  }
}
