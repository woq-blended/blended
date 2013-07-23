package de.woq.osgi.java.itestsupport;

import de.woq.osgi.java.testsupport.XMLMessageFactory;
import org.apache.camel.*;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class CamelTestSupport {

  private CamelContext context;
  private final ConnectionFactory connectionFactory;

  private final AtomicBoolean initialized = new AtomicBoolean(false);

  private final static Logger LOGGER = LoggerFactory.getLogger(CamelTestSupport.class);


  public CamelTestSupport(ConnectionFactory connectionFactory) {
    Assert.assertNotNull("JMS connection factory cannot be null for Camel Test Context");
    this.connectionFactory = connectionFactory;
  }

  public void init() {
    if (!initialized.getAndSet(true)) {
      LOGGER.info("Creating Test Camel Context.");
      context = new DefaultCamelContext();
      context.addComponent("activemq", JmsComponent.jmsComponent(connectionFactory));
    }
  }

  public void sendTestMessage(final String message, final String uri) {

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

    ProducerTemplate producer = getContext().createProducerTemplate();
    producer.send(uri, exchange);
    LOGGER.info("Sent test message to [{}]", uri);
  }

  public CamelContext getContext() {
    return context;
  }

}
