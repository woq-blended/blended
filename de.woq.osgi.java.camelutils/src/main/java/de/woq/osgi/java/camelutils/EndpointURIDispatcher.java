package de.woq.osgi.java.camelutils;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;

public class EndpointURIDispatcher {

  private final EndpointURIFactory uriFactory;

  public EndpointURIDispatcher(EndpointURIFactory uriFactory) {
    this.uriFactory = uriFactory;
  }

  public void dispatch(final Exchange exchange) throws Exception {

    String[] endpointUris = uriFactory.createEndpointUris(exchange);

    if (endpointUris == null || endpointUris.length == 0) {
      throw new Exception("List of dispatch endpoints cannot be empty");
    }

    ProducerTemplate template = exchange.getContext().createProducerTemplate();
    for(String uri: endpointUris) {
      template.send(uri, exchange);
    }
  }
}
