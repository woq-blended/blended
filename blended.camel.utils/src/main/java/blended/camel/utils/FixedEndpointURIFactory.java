package blended.camel.utils;

import org.apache.camel.Exchange;

public final class FixedEndpointURIFactory implements EndpointURIFactory {

  private final String[] endpointUris;

  public FixedEndpointURIFactory(String...endpointUris) {
    this.endpointUris = endpointUris;
  }

  @Override
  public String[] createEndpointUris(final Exchange exchange) throws Exception {
    return endpointUris;
  }
}
