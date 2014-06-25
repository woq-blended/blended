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

package de.woq.blended.itestsupport;

import de.woq.blended.testsupport.XMLMessageFactory;
import de.woq.blended.util.FileReader;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class CamelTestSupport {

  private CamelContext context = null;

  private final static Logger LOGGER = LoggerFactory.getLogger(CamelTestSupport.class);


  public synchronized void init() throws Exception {

    if (context == null) {
      context = new DefaultCamelContext();

      context.addComponent("mock", new MockComponent());
    }
  }

  public void sendTestMessage(
    final String message, final Properties properties, final String uri
  ) throws Exception {
    sendTestMessage(message, properties, uri, true);
  }


  public void sendTestMessage(
    final String message, final Properties properties, final String uri, final boolean evaluteXML
  ) throws Exception {

    Message msg = null;

    if (evaluteXML) {
      msg = createMessageFromXML(message);
    }

    if (msg == null) {
      msg = createMessageFromFile(message, properties);
    }

    if (msg == null) {
      LOGGER.info("Using text as msg body: [{}]", message);
      msg = new DefaultMessage();
      msg.setBody(message);
      addMessageProperties(msg, properties);
    }

    final Exchange sent = new DefaultExchange(getContext(), ExchangePattern.InOnly);
    sent.setIn(msg);

    final ProducerTemplate producer = getContext().createProducerTemplate();
    final Exchange response = producer.send(uri, sent);

    if (response.getException() != null) {
      LOGGER.info("Message not sent to [{}]", uri);
      throw response.getException();
    } else {
      LOGGER.info("Sent test message to [{}]", uri);
    }
  }

  private Message createMessageFromFile(final String message, final Properties props) {

    Message result = null;

    try {
      final byte[] content = FileReader.readFile(message);
      result = new DefaultMessage();
      result.setBody(content);
      LOGGER.info("Body length is [" + content.length + "]");
      addMessageProperties(result, props);
    } catch (Exception e) {
      // ignore
    }

    return result;
  }

  private Message createMessageFromXML(final String message) {

    Message result = null;

    try {
      result = new XMLMessageFactory(message).createMessage();
    } catch (Exception e) {
      // ignore
    }

    return result;
  }

  private void addMessageProperties(final Message msg, final Properties props) {
    if (msg != null && props != null) {
      for(String key: props.stringPropertyNames() ) {
        LOGGER.info("Setting property [{}] = [{}]", key, props.getProperty(key));
        msg.setHeader(key, props.getProperty(key));
      }
    }
  }

  public void sendTestMessage(final String message, final String properties, final String uri) throws Exception {

    Properties props = new Properties();

    if (properties != null) {
      for(String pair : properties.split(";")) {
        String[] keyValue = pair.split("=");
        if (keyValue.length == 2) {
          props.put(keyValue[0].trim(), keyValue[1].trim());
        }
      }
    }

    sendTestMessage(message, props, uri);
  }

  public void sendTestMessage(final String message, final String uri) throws Exception {
    sendTestMessage(message, "", uri);
  }

  public MockEndpoint wireMock(final String mockName, final String uri) throws Exception {

    final MockEndpoint result = (MockEndpoint)getContext().getEndpoint("mock:" + mockName);

    getContext().addRoutes(new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        from(uri).to(result);
      }
    });

    return result;
  }

  public synchronized CamelContext getContext() throws Exception {
    if (context == null) {
      init();
    }
    return context;
  }
}
