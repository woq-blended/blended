package blended.camel.utils;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;

public class EndpointURIDispatcher {

  private final EndpointURIFactory uriFactory;

  public EndpointURIDispatcher(EndpointURIFactory uriFactory) {
    this.uriFactory = uriFactory;
  }

  public void dispatch(final Exchange exchange) throws Exception {

    String[] endpointUris = uriFactory.createEndpointUris(exchange);

    if (endpointUris != null && endpointUris.length > 0) {

      ProducerTemplate template = exchange.getContext().createProducerTemplate();

      try {
        for (String uri : endpointUris) {
          template.send(uri, exchange);
        }
      } finally {
        template.stop();
      }
    }
  }
}
