package de.woq.osgi.java.camelutils;

public final class FixedEndpointURIFactory implements EndpointURIFactory {

  private final String endpointUri;

  public FixedEndpointURIFactory(String endpointUri) {
    this.endpointUri = endpointUri;
  }

  @Override
  public String createEndpointUri() {
    return endpointUri;
  }
}
