package de.woq.osgi.java.activemq.brokerstarter;

import de.woq.osgi.java.startcompletion.StartCompletionService;
import org.apache.activemq.broker.BrokerService;

public class ActiveMQBrokerStarter {

  private final BrokerService broker;
  private final StartCompletionService completionService;
  private final String completionToken;

  public ActiveMQBrokerStarter(
    final BrokerService broker,
    final StartCompletionService completionService,
    final String completionToken

  ) {
    this.broker = broker;
    this.completionService = completionService;
    this.completionToken = completionToken;
  }

  public void init() {
    broker.waitUntilStarted();
    completionService.complete(completionToken);
    // completionService.complete("activemq.broker");
  }
}
