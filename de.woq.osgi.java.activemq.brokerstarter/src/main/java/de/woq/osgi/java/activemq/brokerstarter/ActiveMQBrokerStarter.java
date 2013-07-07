/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
