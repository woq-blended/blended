package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.testsupport.XMLMessageFactory;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class CamelTestSupport {

  private CamelContext context = null;

  private final static Logger LOGGER = LoggerFactory.getLogger(CamelTestSupport.class);


  public synchronized void init() throws Exception {

    if (context == null) {
      context = new DefaultCamelContext();

      context.addComponent("mock", new MockComponent());
    }
  }

  public void sendTestMessage(final String message, final String uri) throws Exception {

    Message msg = null;

    try {
      LOGGER.info("Trying to create message with Message factory [{}]", message);
      msg = new XMLMessageFactory(message).createMessage();
    } catch (Exception e) {
      LOGGER.info("Using text as msg body: [{}]", message);
      msg = new DefaultMessage();
      msg.setBody(message);
    }

    Exchange exchange = new DefaultExchange(getContext(), ExchangePattern.InOnly);
    exchange.setIn(msg);

    ProducerTemplate producer = getContext().createProducerTemplate();
    producer.send(uri, exchange);
    LOGGER.info("Sent test message to [{}]", uri);
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
