package de.woq.osgi.java.camelutils;

import org.apache.camel.Exchange;

public interface EndpointURIFactory {

  public String[] createEndpointUris(final Exchange exchange) throws Exception;
}
