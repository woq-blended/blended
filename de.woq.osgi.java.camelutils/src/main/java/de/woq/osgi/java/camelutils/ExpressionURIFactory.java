package de.woq.osgi.java.camelutils;

import org.apache.camel.Exchange;
import org.apache.camel.builder.SimpleBuilder;

public class ExpressionURIFactory implements EndpointURIFactory {

  private final String expression;

  public ExpressionURIFactory(String expression) {
    this.expression = expression;
  }

  @Override
  public String[] createEndpointUris(Exchange exchange) throws Exception {
    SimpleBuilder builder = new SimpleBuilder(expression);
    return new String[] { builder.evaluate(exchange, String.class) };
  }
}
