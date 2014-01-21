package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.testsupport.XMLMessageFactory;
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

  public void sendTestMessage(final String message, final Properties properties, final String uri) throws Exception {
    Message msg = null;

    try {
      LOGGER.info("Trying to create message with Message factory [{}]", message);
      msg = new XMLMessageFactory(message).createMessage();
    } catch (Exception e) {
      LOGGER.info("Using text as msg body: [{}]", message);
      msg = new DefaultMessage();
      msg.setBody(message);

      if (properties != null) {
        for(String key: properties.stringPropertyNames() ) {
          LOGGER.info("Setting property [{}] = [{}]", key, properties.getProperty(key));
          msg.setHeader(key, properties.getProperty(key));
        }
      }
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
