package de.woq.osgi.java.camelutils;

import org.apache.camel.Message;

public abstract class MessageEndpointURIFactory implements EndpointURIFactory {

  private final Message message;

  public MessageEndpointURIFactory(final Message message) {
    this.message = message;
  }

  @Override
  public String createEndpointUri() {
    return createEndpointUri(message);
  }

  protected abstract String createEndpointUri(final Message message);
}
