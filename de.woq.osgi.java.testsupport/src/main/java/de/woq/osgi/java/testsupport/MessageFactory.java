package de.woq.osgi.java.testsupport;

import org.apache.camel.Message;

public interface MessageFactory {

  public Message createMessage() throws Exception;
}
